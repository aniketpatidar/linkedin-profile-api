(ns linkedin-profile-api.cookies
  "Owns session resolution for LinkedIn authentication: extracting the li_at
  cookie, programmatic login with email/password plus JSESSIONID warmup for
  CSRF, and the ensure-cookie contract consumed by the server layer.

  This is a pure core module: it performs no HTTP of its own. Real HTTP calls
  (`babashka.http-client` get/post) are injected by the IO-near adapter that
  wires this module, keeping transport details out of the core."
  (:require [linkedin-profile-api.gateway :as gateway]
            [clojure.string :as str]))

(defn- extract-li-at-from-string [s]
  (when-let [m (re-find #"(?i)\bli_at=([^;]+)" s)]
    (second m)))

(defn- li-at-in-entry [c]
  (extract-li-at-from-string
    (if (map? c)
      (str (get c :name) "=" (get c :value))
      (str c))))

(defn extract-li-at
  "Extract the li_at session cookie value from a cookie collection. Accepts a
  map (name -> value), a vector of name=value strings, or a single string."
  [cookies]
  (cond
    (map? cookies)
    (get cookies "li_at")

    (coll? cookies)
    (some li-at-in-entry cookies)

    (string? cookies)
    (extract-li-at-from-string cookies)))

(defn- url-encode [s]
  (java.net.URLEncoder/encode (str s) "UTF-8"))

(defn- login-page-csrf [body]
  (when-let [m (or (re-find #"name=\"loginCsrfParam\" value=\"([^\"]*)\"" body)
                   (re-find #"loginCsrfParam[\"']?\s*[:=]\s*[\"']([^\"']*)" body))]
    (second m)))

(defn login-with
  "Attempt a programmatic LinkedIn login with email/password to obtain a li_at
  session cookie. `http-get`/`http-post` must be provided by the caller (see
  the IO adapter that wires this module). Returns the li_at cookie value or
  nil when the flow could not establish a session."
  [{:keys [email password http-get http-post]}]
  (try
    (let [login-get (http-get "https://www.linkedin.com/uas/login"
                              {:headers {"User-Agent" "Mozilla/5.0 (compatible; LinkedInProfileAPI/1.0)"}
                               :throw false :timeout 20000})
          csrf-val (or (login-page-csrf (:body login-get)) "")
          resp (http-post "https://www.linkedin.com/uas/authenticate"
                          {:headers {"Content-Type" "application/x-www-form-urlencoded"
                                     "User-Agent" "Mozilla/5.0 (compatible; LinkedInProfileAPI/1.0)"}
                           :throw false :timeout 30000
                           :form-params {"session_key" email
                                         "session_password" password
                                         "loginCsrfParam" csrf-val
                                         "signin" "true"}})
          cookie-header (or (get (:headers resp) "set-cookie")
                            (get (:headers resp) "Set-Cookie"))]
      (extract-li-at cookie-header))
    (catch Exception _ nil)))

(defn login-cookie
  "Obtain a li_at session cookie. If a cookie is already configured it is
  returned directly; otherwise attempts a programmatic login with
  email/password. Returns {:cookie <value-or-nil>}. The caller supplies the
  HTTP get/post functions (see the IO adapter wiring this module)."
  [config & [{:keys [login-fn http-get http-post]
              :or {login-fn login-with}}]]
  (if (and (:cookie config) (not (empty? (:cookie config))))
    {:cookie (:cookie config)}
    {:cookie (login-fn (assoc config :http-get http-get :http-post http-post))}))

(defn- extract-jsessionid
  "Extract the JSESSIONID cookie value from a Set-Cookie header value."
  [set-cookie]
  (when (string? set-cookie)
    (second (re-find #"(?i)JSESSIONID=([^;\s]+)" set-cookie))))

(defn warmup-session
  "Best-effort session warmup: request the LinkedIn homepage to obtain a fresh
  JSESSIONID cookie, which Voyager requires as the csrf-token. The caller
  supplies the http-get function. Returns a csrf-token value or nil when
  LinkedIn did not issue one."
  [http-get]
  (try
    (let [resp (http-get "https://www.linkedin.com/"
                         {:headers {"User-Agent" "Mozilla/5.0 (compatible; LinkedInProfileAPI/1.0)"}
                          :throw false :timeout 15000})
          set-cookies (:headers resp)
          jsessionid (or (extract-jsessionid (get set-cookies "Set-Cookie"))
                         (extract-jsessionid (get set-cookies "set-cookie")))]
      (when (and jsessionid (not (empty? jsessionid)))
        (str/replace jsessionid #"^\"|\"$" "")))
    (catch Exception _ nil)))

(defn ensure-cookie
  "Resolve the session cookie for a config, returning either {:cookie <value>}
  when a cookie is available, or {:status :error :code :upstream_error ...}
  when login could not establish a session."
  [config & [opts]]
  (let [{:keys [cookie]} (login-cookie config opts)]
    (if (and cookie (not (empty? cookie)))
      {:cookie cookie}
      (gateway/error-result :upstream_error
                            "Could not establish a LinkedIn session with the configured credentials."))))

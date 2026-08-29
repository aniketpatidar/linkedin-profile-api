(ns linkedin-profile-api.upstream
  (:require [linkedin-profile-api.voyager :as voyager]
            [cheshire.core :as json]
            [babashka.http-client :as http]
            [clojure.string :as str]))

(defn encode-json [x] (json/generate-string x))

(defn decode-json [s] (json/parse-string s true))

(defn voyager-profile-url [public-id]
  (str "https://www.linkedin.com/voyager/api/identity/profiles/" public-id))

(defn- request-headers
  "Build the request headers for a Voyager call, using the session cookie."
  [config]
  (let [cookie (:cookie config)
        csrf (:csrf-token config)
        cookie-header (str "li_at=" cookie
                           (when csrf (str "; JSESSIONID=\"" csrf "\"")))]
    (cond-> {"User-Agent" "Mozilla/5.0 (compatible; LinkedInProfileAPI/1.0)"
             "Accept" "application/json"
             "x-restli-protocol-version" "2.0.0"
             "Cookie" cookie-header}
      csrf
      (assoc "csrf-token" csrf))))

(defn- classify-response
  "Turn an upstream HTTP response (or thrown exception) into a fetch result."
  [response]
  (cond
    (and (map? response) (= 200 (:status response)))
    (let [parsed (try (decode-json (:body response))
                      (catch Exception _ nil))]
      {:status :ok
       :profile (voyager/normalize (or parsed {}))})

    (and (map? response) (= 404 (:status response)))
    {:status :error :code :profile_not_found
     :message "The requested LinkedIn profile was not found or is not viewable with the configured credentials."}

    :else
    {:status :error :code :upstream_error
     :message "The upstream LinkedIn request failed."}))

(def ^:private default-http-get
  (fn [url opts] (http/get url opts)))

(defn- extract-jsessionid
  "Extract the JSESSIONID cookie value from a Set-Cookie header value."
  [set-cookie]
  (when (string? set-cookie)
    (second (re-find #"(?i)JSESSIONID=([^;\s]+)" set-cookie))))

(defn- warmup-session
  "Best-effort session warmup: request the LinkedIn homepage to obtain a fresh
  JSESSIONID cookie, which Voyager requires as the csrf-token. Returns a
  csrf-token value or nil when LinkedIn did not issue one."
  [{:keys [http-get] :or {http-get default-http-get}}]
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

(defn fetch-profile
  "Fetch and normalize a LinkedIn profile by public id.

  Returns either {:status :ok :profile <normalized-raw>} or
  {:status :error :code <code> :message <msg>} where code is :profile_not_found
  or :upstream_error. Accepts an injectable :http-get (defaults to the real
  babashka HTTP client) so the classification logic is testable."
  [{:keys [public-id config http-get]
    :or {http-get default-http-get}}]
  (try
    (let [csrf (warmup-session {:http-get http-get})
          headers (request-headers (assoc config :csrf-token csrf))
          url (voyager-profile-url public-id)
          response (http-get url {:headers headers :as :string
                                  :throw false :timeout 20000})]
      (classify-response response))
    (catch Exception e
      {:status :error :code :upstream_error
       :message (str "The upstream LinkedIn request failed: " (.getMessage e))})))

(defn extract-li-at
  "Extract the li_at session cookie value from a cookie collection. Accepts a
  map (name -> value), a vector of name=value strings, or a single string."
  [cookies]
  (cond
    (map? cookies)
    (get cookies "li_at")

    (coll? cookies)
    (some (fn [c]
            (let [c (if (map? c) (str (get c :name) "=" (get c :value)) (str c))]
              (when-let [m (re-find #"(?i)\bli_at=([^;]+)" c)]
                (second m))))
          cookies)

    (string? cookies)
    (when-let [m (re-find #"(?i)\bli_at=([^;]+)" cookies)]
      (second m))))

(defn- url-encode [s]
  (java.net.URLEncoder/encode (str s) "UTF-8"))

(defn- default-login
  "Best-effort programmatic login to LinkedIn to obtain a session cookie using
  email/password credentials. Follows the classic auth flow: load the login
  page, capture the loginCsrfParam, then POST the credentials and read the
  li_at cookie from the response. Returns a li_at cookie value or nil."
  [{:keys [email password]}]
  (try
    (let [login-get (http/get "https://www.linkedin.com/uas/login"
                              {:headers {"User-Agent" "Mozilla/5.0 (compatible; LinkedInProfileAPI/1.0)"}
                               :throw false :timeout 20000})
          csrf (or (re-find #"name=\"loginCsrfParam\" value=\"([^\"]*)\"" (:body login-get))
                   (re-find #"loginCsrfParam[\"']?\s*[:=]\s*[\"']([^\"']*)" (:body login-get)))
          csrf-val (if csrf (second csrf) "")
          form (str "session_key=" (url-encode email)
                    "&session_password=" (url-encode password)
                    "&loginCsrfParam=" (url-encode csrf-val)
                    "&signin=true")
          resp (http/post "https://www.linkedin.com/uas/authenticate"
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
  email/password. Returns {:cookie <value-or-nil>}."
  [config & [{:keys [login-fn] :or {login-fn default-login}}]]
  (if (and (:cookie config) (not (empty? (:cookie config))))
    {:cookie (:cookie config)}
    {:cookie (login-fn config)}))

(defn ensure-cookie
  "Resolve the session cookie for a config, returning either {:cookie <value>}
  when a cookie is available, or {:status :error :code :upstream_error ...}
  when login could not establish a session."
  [config & [opts]]
  (let [{:keys [cookie]} (login-cookie config opts)]
    (if (and cookie (not (empty? cookie)))
      {:cookie cookie}
      {:status :error :code :upstream_error
       :message "Could not establish a LinkedIn session with the configured credentials."})))

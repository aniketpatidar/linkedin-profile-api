(ns linkedin-profile-api.upstream-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.upstream :as upstream]
            [clojure.string :as str]))

(defn- fake-http
  "Build an injectable http-get that returns a fixed response or throws."
  ([status body] (fn [_url _opts] {:status status :body body}))
  ([err] (fn [_url _opts] (throw err))))

(defn- fake-http-csrf
  "Inject an http-get whose response carries a JSESSIONID set-cookie (so the
  warmup yields a csrf-token) and then delegates to `next` for the profile call."
  [jsessionid next]
  (fn [url opts]
    (if (not (str/includes? url "/voyager/"))
      {:status 200 :headers {"Set-Cookie" (str "JSESSIONID=\"" jsessionid "\"; Path=/")}}
      (next url opts))))

(describe "linkedin-profile-api.upstream/voyager-profile-url"
  (it "builds the voyager identity endpoint for a public id"
    (should= "https://www.linkedin.com/voyager/api/identity/profiles/janedoe"
             (upstream/voyager-profile-url "janedoe"))))

(describe "linkedin-profile-api.upstream/fetch-profile"
  (it "returns :status :ok and the normalized profile on a 200 response"
    (let [body {:firstName "Jane" :lastName "Doe" :headline "Eng"
                :positionView [{:title "Engineer" :companyName "Acme"}]}
          result (upstream/fetch-profile
                   {:public-id "janedoe"
                    :config {:cookie "abc" :email nil :password nil}
                    :http-get (fake-http 200 (upstream/encode-json body))})]
      (should= :ok (:status result))
      (should= "Jane Doe" (get-in result [:profile :name]))
      (should= [{:title "Engineer" :company "Acme"}] (get-in result [:profile :experience]))))
  (it "returns :profile_not_found when the upstream responds 404"
    (let [result (upstream/fetch-profile
                   {:public-id "janedoe"
                    :config {:cookie "abc" :email nil :password nil}
                    :http-get (fake-http 404 "{}")})]
      (should= :error (:status result))
      (should= :profile_not_found (:code result))))
  (it "returns :upstream_error on a network exception"
    (let [result (upstream/fetch-profile
                   {:public-id "janedoe"
                    :config {:cookie "abc" :email nil :password nil}
                    :http-get (fake-http (ex-info "boom" {}))})]
      (should= :error (:status result))
      (should= :upstream_error (:code result))))
  (it "returns :upstream_error on a 5xx upstream response"
    (let [result (upstream/fetch-profile
                   {:public-id "janedoe"
                    :config {:cookie "abc" :email nil :password nil}
                    :http-get (fake-http 503 "{}")})]
      (should= :error (:status result))
      (should= :upstream_error (:code result))))
  (it "returns :upstream_error on an auth failure (401)"
    (let [result (upstream/fetch-profile
                   {:public-id "janedoe"
                    :config {:cookie "abc" :email nil :password nil}
                    :http-get (fake-http 401 "{}")})]
      (should= :error (:status result))
      (should= :upstream_error (:code result))))
  (it "sends the li_at session cookie when a cookie is configured"
    (let [seen (atom nil)
          http (fn [url opts] (reset! seen {:url url :opts opts})
                            {:status 404 :body "{}"})
          _ (upstream/fetch-profile {:public-id "janedoe"
                                     :config {:cookie "abc" :email nil :password nil}
                                     :http-get http})]
      (should= "li_at=abc" (get-in @seen [:opts :headers "Cookie"])))
    (let [seen (atom nil)
          http (fn [url opts] (reset! seen {:url url :opts opts})
                            {:status 200 :body "{}"})
          _ (upstream/fetch-profile {:public-id "janedoe"
                                     :config {:cookie "abc" :email nil :password nil}
                                     :http-get http})]
      (should= "li_at=abc" (get-in @seen [:opts :headers "Cookie"]))))
  (it "sends the csrf-token header and JSESSIONID cookie after a warmup"
    (let [seen (atom nil)
          http (fake-http-csrf "ajax:12345"
                               (fn [url opts] (reset! seen {:url url :opts opts})
                                         {:status 200 :body "{}"}))
          _ (upstream/fetch-profile {:public-id "janedoe"
                                     :config {:cookie "abc" :email nil :password nil}
                                     :http-get http})]
      (should= "ajax:12345" (get-in @seen [:opts :headers "csrf-token"]))
      (should= "li_at=abc; JSESSIONID=\"ajax:12345\"" (get-in @seen [:opts :headers "Cookie"]))))
  (it "procile fetch still succeeds when the warmup issues no JSESSIONID"
    (let [result (upstream/fetch-profile
                   {:public-id "janedoe"
                    :config {:cookie "abc" :email nil :password nil}
                    :http-get (fake-http 200 "{}")})]
      (should= :ok (:status result))))
  (it "does not add a csrf-token header when the warmup finds no JSESSIONID"
    (let [seen (atom nil)
          http (fn [url opts] (reset! seen {:url url :opts opts})
                            {:status 200 :body "{}"})
          _ (upstream/fetch-profile {:public-id "janedoe"
                                     :config {:cookie "abc" :email nil :password nil}
                                     :http-get http})]
      (should= nil (get-in @seen [:opts :headers "csrf-token"]))
      (should= "li_at=abc" (get-in @seen [:opts :headers "Cookie"])))))

(describe "linkedin-profile-api.upstream/extract-li-at"
  (it "extracts li_at from a set-cookie header string"
    (should= "abc123"
             (upstream/extract-li-at "li_at=abc123; Domain=.linkedin.com; Path=/; Max-Age=1234")))
  (it "extracts li_at from a vector of cookie strings"
    (should= "xyz" (upstream/extract-li-at ["JSESSIONID=foo" "li_at=xyz; Path=/"])))
  (it "returns nil when li_at is absent"
    (should= nil (upstream/extract-li-at ["JSESSIONID=foo"])))
  (it "returns nil for nil input"
    (should= nil (upstream/extract-li-at nil))))

(describe "linkedin-profile-api.upstream/login-cookie"
  (it "uses the configured cookie directly when present"
    (should= {:cookie "abc"}
             (upstream/login-cookie {:cookie "abc" :email nil :password nil})))
  (it "calls the login function when no cookie is configured"
    (let [called (atom false)
          login (fn [_] (reset! called true) "newcookie")]
      (should= {:cookie "newcookie"}
               (upstream/login-cookie {:email "a@b.c" :password "pw" :cookie nil}
                                      {:login-fn login}))
      (should @called)))
  (it "passes the credentials to the login function"
    (let [creds (atom nil)
          login (fn [c] (reset! creds c) "newcookie")]
      (upstream/login-cookie {:email "a@b.c" :password "pw" :cookie nil}
                             {:login-fn login})
      (should= {:email "a@b.c" :password "pw" :cookie nil} @creds))))

(describe "linkedin-profile-api.upstream/ensure-cookie"
  (it "returns the cookie when login yields one"
    (should= {:cookie "newcookie"}
             (upstream/ensure-cookie {:email "a@b.c" :password "pw" :cookie nil}
                                     {:login-fn (fn [_] "newcookie")})))
  (it "returns an upstream_error when login yields no cookie"
    (let [result (upstream/ensure-cookie {:email "a@b.c" :password "pw" :cookie nil}
                                         {:login-fn (fn [_] nil)})]
      (should= :error (:status result))
      (should= :upstream_error (:code result)))))

(describe "linkedin-profile-api.upstream/default-login"

  (defn- fake-login-http
    "Build injectable http-get/http-post for the login flow. `login-body`
    arms the GET used on the login page; `cookie-header` arms the POST used on
    authenticate."
    [login-body cookie-header]
    {:http-get (fn [_url _opts] {:status 200 :body login-body})
     :http-post (fn [_url _opts] {:status 200 :headers {"set-cookie" cookie-header}})})

  (it "logs in and extracts li_at from the authenticate response"
    (let [{:keys [http-get http-post]} (fake-login-http
                                         "<form><input type=\"hidden\" name=\"loginCsrfParam\" value=\"t0ken\"></form>"
                                         "li_at=ses1; Domain=.linkedin.com; Path=/")
          result (#'upstream/default-login {:email "a@b.c" :password "pw"
                                             :http-get http-get :http-post http-post})]
      (should= "ses1" result)))
  (it "falls back to the alternate loginCsrfParam regex"
    (let [{:keys [http-get http-post]} (fake-login-http
                                         "var csrf = { loginCsrfParam: 'alt' };"
                                         "li_at=ses2; path=/")
          result (#'upstream/default-login {:email "a@b.c" :password "pw"
                                             :http-get http-get :http-post http-post})]
      (should= "ses2" result)))
  (it "returns nil when the authenticate response carries no set-cookie"
    (let [{:keys [http-get http-post]} (fake-login-http
                                         "<form><input name=\"loginCsrfParam\" value=\"t\"></form>"
                                         nil)
          result (#'upstream/default-login {:email "a@b.c" :password "pw"
                                             :http-get http-get :http-post http-post})]
      (should= nil result)))
  (it "returns nil when the login page request fails"
    (let [result (#'upstream/default-login {:email "a@b.c" :password "pw"
                                             :http-get (fn [_ _] (throw (ex-info "boom" {})))
                                             :http-post (fn [_ _] {:status 200 :headers {}})})]
      (should= nil result))))

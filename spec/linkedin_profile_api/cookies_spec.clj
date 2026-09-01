(ns linkedin-profile-api.cookies-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.cookies :as cookies]))

(defn fake-login-http
  "Build injectable http-get/http-post for the login flow. `login-body`
  arms the GET used on the login page; `cookie-header` arms the POST used on
  authenticate."
  [login-body cookie-header]
  {:http-get (fn [_url _opts] {:status 200 :body login-body})
   :http-post (fn [_url _opts] {:status 200 :headers {"set-cookie" cookie-header}})})

(describe "linkedin-profile-api.cookies/extract-li-at"
  (it "extracts li_at from a set-cookie header string"
    (should= "abc123"
             (cookies/extract-li-at "li_at=abc123; Domain=.linkedin.com; Path=/; Max-Age=1234")))
  (it "extracts li_at from a vector of cookie strings"
    (should= "xyz" (cookies/extract-li-at ["JSESSIONID=foo" "li_at=xyz; Path=/"])))
  (it "returns nil when li_at is absent"
    (should= nil (cookies/extract-li-at ["JSESSIONID=foo"])))
  (it "returns nil for nil input"
    (should= nil (cookies/extract-li-at nil))))

(describe "linkedin-profile-api.cookies/login-with"
  (it "logs in and extracts li_at from the authenticate response"
    (let [{:keys [http-get http-post]} (fake-login-http
                                         "<form><input type=\"hidden\" name=\"loginCsrfParam\" value=\"t0ken\"></form>"
                                         "li_at=ses1; Domain=.linkedin.com; Path=/")
          result (cookies/login-with {:email "a@b.c" :password "pw"
                                      :http-get http-get :http-post http-post})]
      (should= "ses1" result)))
  (it "falls back to the alternate loginCsrfParam regex"
    (let [{:keys [http-get http-post]} (fake-login-http
                                         "var csrf = { loginCsrfParam: 'alt' };"
                                         "li_at=ses2; path=/")
          result (cookies/login-with {:email "a@b.c" :password "pw"
                                      :http-get http-get :http-post http-post})]
      (should= "ses2" result)))
  (it "returns nil when the authenticate response carries no set-cookie"
    (let [{:keys [http-get http-post]} (fake-login-http
                                         "<form><input name=\"loginCsrfParam\" value=\"t\"></form>"
                                         nil)
          result (cookies/login-with {:email "a@b.c" :password "pw"
                                      :http-get http-get :http-post http-post})]
      (should= nil result)))
  (it "returns nil when the login page request fails"
    (let [result (cookies/login-with {:email "a@b.c" :password "pw"
                                      :http-get (fn [_ _] (throw (ex-info "boom" {})))
                                      :http-post (fn [_ _] {:status 200 :headers {}})})]
      (should= nil result))))

(describe "linkedin-profile-api.cookies/login-cookie"
  (it "uses the configured cookie directly when present"
    (should= {:cookie "abc"}
             (cookies/login-cookie {:cookie "abc" :email nil :password nil})))
  (it "calls the login function when no cookie is configured"
    (let [called (atom false)
          login (fn [_] (reset! called true) "newcookie")]
      (should= {:cookie "newcookie"}
               (cookies/login-cookie {:email "a@b.c" :password "pw" :cookie nil}
                                     {:login-fn login}))
      (should @called)))
  (it "passes the credentials to the login function"
    (let [creds (atom nil)
          login (fn [c] (reset! creds c) "newcookie")]
      (cookies/login-cookie {:email "a@b.c" :password "pw" :cookie nil}
                            {:login-fn login})
      (should= {:email "a@b.c" :password "pw" :cookie nil}
               (select-keys @creds [:email :password :cookie])))))

(describe "linkedin-profile-api.cookies/ensure-cookie"
  (it "returns the cookie when login yields one"
    (should= {:cookie "newcookie"}
             (cookies/ensure-cookie {:email "a@b.c" :password "pw" :cookie nil}
                                    {:login-fn (fn [_] "newcookie")})))
  (it "returns an upstream_error when login yields no cookie"
    (let [result (cookies/ensure-cookie {:email "a@b.c" :password "pw" :cookie nil}
                                        {:login-fn (fn [_] nil)})]
      (should= :error (:status result))
      (should= :upstream_error (:code result)))))

(describe "linkedin-profile-api.cookies/warmup-session"
  (it "extracts a JSESSIONID csrf-token from a warmup response"
    (let [http-get (fn [_ _] {:status 200 :headers {"Set-Cookie" "JSESSIONID=\"ajax:123\"; Path=/"}})]
      (should= "ajax:123" (cookies/warmup-session http-get))))
  (it "returns nil when no JSESSIONID is issued"
    (let [http-get (fn [_ _] {:status 200 :headers {}})]
      (should= nil (cookies/warmup-session http-get))))
  (it "returns nil when the warmup request fails"
    (let [http-get (fn [_ _] (throw (ex-info "boom" {})))]
      (should= nil (cookies/warmup-session http-get)))))

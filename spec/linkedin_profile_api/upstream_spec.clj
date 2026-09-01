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

(describe "linkedin-profile-api.upstream/ensure-cookie (IO adapter)"
  (it "returns the configured cookie directly without attempting login"
    (let [result (upstream/ensure-cookie {:cookie "abc"})]
      (should= {:cookie "abc"} result)))
  (it "delegates programmatic login to the pure cookies logic with HTTP bound"
    (let [http-get (fn [_u _o] {:status 200 :body "<form><input name=\"loginCsrfParam\" value=\"csrf\"/></form>"})
          http-post (fn [_u _o] {:status 200 :headers {"set-cookie" "li_at=xyz; Path=/"}})
          result (upstream/ensure-cookie {:email "a@b.com" :password "pw"}
                                         {:http-get http-get :http-post http-post})]
      (should= {:cookie "xyz"} result)))
  (it "returns :upstream_error when login cannot establish a session"
    (let [http-get (fn [_u _o] {:status 200 :body "no csrf here"})
          http-post (fn [_u _o] {:status 200 :headers {"set-cookie" "foo=bar"}})
          result (upstream/ensure-cookie {:email "a@b.com" :password "pw"}
                                         {:http-get http-get :http-post http-post})]
      (should= :error (:status result))
      (should= :upstream_error (:code result)))))

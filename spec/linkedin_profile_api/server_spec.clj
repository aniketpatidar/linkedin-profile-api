(ns linkedin-profile-api.server-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.server :as server]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def fixed-now "2026-08-30T01:00:00Z")

(defn json [s] (json/parse-string s true))

(defn ok-deps [& {:as extra}]
  (merge {:env {"LINKEDIN_COOKIE" "abc"
                "LINKEDIN_EMAIL" "a@b.c"
                "LINKEDIN_PASSWORD" "pw"}
          :now (fn [] fixed-now)
          :ensure-cookie (fn [_] {:cookie "abc"})
          :fetch-profile (fn [{:keys [public-id]}]
                           {:status :ok
                            :profile {:name "Jane Doe"
                                      :headline "Eng"
                                      :experience [{:title "Engineer" :company "Acme"}]}})}
         extra))

(defn req [& {:as r}]
  (merge {:request-method :get :uri "/" :query-string nil} r))

(describe "linkedin-profile-api.server/health"
  (it "returns 200 with status ok"
    (let [r (server/handle-request (ok-deps) (req :uri "/health"))]
      (should= 200 (:status r))
      (should= "ok" (:status (json (:body r)))))))

(describe "linkedin-profile-api.server/unknown route"
  (it "returns 404 for an unknown path"
    (let [r (server/handle-request (ok-deps) (req :uri "/nope"))]
      (should= 404 (:status r)))))

(describe "linkedin-profile-api.server/profile missing url"
  (it "returns 400 invalid_url when no url is supplied"
    (let [r (server/handle-request (ok-deps) (req :uri "/profile" :query-string nil))]
      (should= 400 (:status r))
      (should= "invalid_url" (get-in (json (:body r)) [:error :code]))))
  (it "returns 400 invalid_url for an empty url"
    (let [r (server/handle-request (ok-deps) (req :uri "/profile" :query-string "url="))]
      (should= 400 (:status r))
      (should= "invalid_url" (get-in (json (:body r)) [:error :code])))))

(describe "linkedin-profile-api.server/profile invalid urls"
  (it "rejects a non-url"
    (let [r (server/handle-request (ok-deps)
                                   (req :uri "/profile" :query-string "url=not%20a%20url"))]
      (should= 400 (:status r))
      (should= "invalid_url" (get-in (json (:body r)) [:error :code]))))
  (it "rejects a foreign-domain url"
    (let [r (server/handle-request (ok-deps)
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fexample.com%2Fin%2Fjanedoe"))]
      (should= 400 (:status r))))
  (it "rejects a company url"
    (let [r (server/handle-request (ok-deps)
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fwww.linkedin.com%2Fcompany%2Facme"))]
      (should= 400 (:status r))))
  (it "rejects a profile url with an empty public id"
    (let [r (server/handle-request (ok-deps)
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fwww.linkedin.com%2Fin%2F"))]
      (should= 400 (:status r)))))

(describe "linkedin-profile-api.server/profile missing credentials"
  (it "returns 503 missing_credentials when no credentials are configured"
    (let [r (server/handle-request (ok-deps :env {})
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fjanedoe"))]
      (should= 503 (:status r))
      (should= "missing_credentials" (get-in (json (:body r)) [:error :code])))))

(describe "linkedin-profile-api.server/profile success"
  (it "returns 200 with the profile json"
    (let [r (server/handle-request (ok-deps)
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fjanedoe"))
          body (json (:body r))]
      (should= 200 (:status r))
      (should= "Jane Doe" (:name body))
      (should= "https://www.linkedin.com/in/janedoe" (:url body))
      (should= fixed-now (:fetched_at body))
      (should= [{:title "Engineer" :company "Acme"}] (:experience body))))
  (it "passes the extracted public id and cookie to fetch-profile"
    (let [seen (atom nil)
          deps (ok-deps :fetch-profile (fn [opts] (reset! seen opts)
                                        {:status :ok :profile {:name "Jane"}}))
          _ (server/handle-request deps
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fjanedoe"))]
      (should= "janedoe" (:public-id @seen))
      (should= "abc" (get-in @seen [:config :cookie])))))

(describe "linkedin-profile-api.server/profile upstream errors"
  (it "returns 404 profile_not_found when the profile is unavailable"
    (let [deps (ok-deps :fetch-profile (fn [_] {:status :error :code :profile_not_found :message "no"}))
          r (server/handle-request deps
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fjanedoe"))]
      (should= 404 (:status r))
      (should= "profile_not_found" (get-in (json (:body r)) [:error :code]))))
  (it "returns 502 upstream_error when the upstream request fails"
    (let [deps (ok-deps :fetch-profile (fn [_] {:status :error :code :upstream_error :message "boom"}))
          r (server/handle-request deps
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fjanedoe"))]
      (should= 502 (:status r))
      (should= "upstream_error" (get-in (json (:body r)) [:error :code]))))
  (it "returns 502 upstream_error when a session cannot be established"
    (let [deps (ok-deps :ensure-cookie (fn [_] {:status :error :code :upstream_error :message "no session"}))
          r (server/handle-request deps
                                   (req :uri "/profile" :query-string "url=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fjanedoe"))]
      (should= 502 (:status r))
      (should= "upstream_error" (get-in (json (:body r)) [:error :code])))))

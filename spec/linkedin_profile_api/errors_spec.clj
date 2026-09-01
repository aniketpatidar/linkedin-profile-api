(ns linkedin-profile-api.errors-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.errors :as errors]))

(describe "linkedin-profile-api.errors/status-for-code"
  (it "maps invalid_url to 400"
    (should= 400 (errors/status-for-code :invalid_url)))
  (it "maps profile_not_found to 404"
    (should= 404 (errors/status-for-code :profile_not_found)))
  (it "maps missing_credentials to 503"
    (should= 503 (errors/status-for-code :missing_credentials)))
  (it "maps upstream_error to 502"
    (should= 502 (errors/status-for-code :upstream_error))))

(describe "linkedin-profile-api.errors/error-response"
  (it "builds the error body with code and message"
    (should= {:error {:code "invalid_url" :message "The url is not a valid LinkedIn profile url."}}
             (errors/error-response :invalid_url "The url is not a valid LinkedIn profile url.")))
  (it "serializes the code keyword as a string"
    (let [body (errors/error-response :upstream_error "boom")]
      (should= "upstream_error" (get-in body [:error :code]))))
  (it "keeps a non-empty message"
    (let [body (errors/error-response :missing_credentials "missing")]
      (should= "missing" (get-in body [:error :message])))))

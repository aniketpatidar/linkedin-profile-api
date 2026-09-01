(ns linkedin-profile-api.gateway-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.gateway :as gateway]))

(describe "linkedin-profile-api.gateway/ok-result"
  (it "builds a successful result carrying the profile"
    (should= {:status :ok :profile {:name "Jane"}}
             (gateway/ok-result {:name "Jane"}))))

(describe "linkedin-profile-api.gateway/error-result"
  (it "builds a failed result carrying code and message"
    (should= {:status :error :code :upstream_error :message "boom"}
             (gateway/error-result :upstream_error "boom"))))

(describe "linkedin-profile-api.gateway/success?"
  (it "is true for an ok result"
    (should (gateway/success? (gateway/ok-result {}))))
  (it "is false for an error result"
    (should-not (gateway/success? (gateway/error-result :upstream_error "x"))))
  (it "is false for a non-result map"
    (should-not (gateway/success? {:status :other}))
    (should-not (gateway/success? nil))))

(describe "linkedin-profile-api.gateway/error?"
  (it "is true for an error result"
    (should (gateway/error? (gateway/error-result :upstream_error "x"))))
  (it "is false for an ok result"
    (should-not (gateway/error? (gateway/ok-result {}))))
  (it "is false for a non-result map"
    (should-not (gateway/error? {:status :other}))
    (should-not (gateway/error? nil))))

(describe "linkedin-profile-api.gateway/accessors"
  (it "extracts the profile from an ok result"
    (should= {:name "Jane"} (gateway/profile (gateway/ok-result {:name "Jane"}))))
  (it "extracts code and message from an error result"
    (let [r (gateway/error-result :profile_not_found "gone")]
      (should= :profile_not_found (gateway/code r))
      (should= "gone" (gateway/message r))))
  (it "extracts the session cookie, or nil when absent"
    (should= "abc" (gateway/session-cookie {:cookie "abc"}))
    (should= nil (gateway/session-cookie {:status :error}))))

(describe "linkedin-profile-api.gateway result round trip"
  (it "an ok result is not an error and vice versa"
    (let [ok (gateway/ok-result {:name "Jane"})
          err (gateway/error-result :upstream_error "boom")]
      (should (gateway/success? ok))
      (should-not (gateway/error? ok))
      (should (gateway/error? err))
      (should-not (gateway/success? err)))))

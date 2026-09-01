(ns linkedin-profile-api.config-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.config :as config]))

(describe "linkedin-profile-api.config/credentials"
  (it "reads LINKEDIN_EMAIL, LINKEDIN_PASSWORD, LINKEDIN_COOKIE from the env"
    (should= {:email "a@b.c" :password "pw" :cookie "ct"}
             (config/credentials {"LINKEDIN_EMAIL" "a@b.c"
                                  "LINKEDIN_PASSWORD" "pw"
                                  "LINKEDIN_COOKIE" "ct"})))
  (it "returns nils when unset"
    (should= {:email nil :password nil :cookie nil}
             (config/credentials {}))))

(describe "linkedin-profile-api.config/credentials-available?"
  (it "is true when a cookie is present"
    (should (config/credentials-available? {:cookie "ct" :email nil :password nil})))
  (it "is true when email and password are present"
    (should (config/credentials-available? {:email "a@b.c" :password "pw" :cookie nil})))
  (it "is false when nothing is present"
    (should-not (config/credentials-available? {:email nil :password nil :cookie nil})))
  (it "is false for a zero-length cookie"
    (should-not (config/credentials-available? {:cookie "" :email nil :password nil})))
  (it "is false when only email is present"
    (should-not (config/credentials-available? {:email "a@b.c" :password nil :cookie nil})))
  (it "is false when only password is present"
    (should-not (config/credentials-available? {:email nil :password "pw" :cookie nil}))))

(ns linkedin-profile-api.config-property
  (:require [property.runner :refer [check!]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [linkedin-profile-api.config :as config]))

(def field-value
  (gen/one-of [(gen/return nil)
               (gen/return "")
               gen/string-alphanumeric]))

(def creds-gen
  (gen/hash-map :cookie field-value
                :email field-value
                :password field-value))

(defn expected-available? [{:keys [cookie email password]}]
  (or (and cookie (not (str/blank? cookie)))
      (and (not (str/blank? email))
           (not (str/blank? password)))))

(check! "credentials-available? matches the documented auth rule"
  (prop/for-all [creds creds-gen]
    (= (boolean (expected-available? creds))
       (boolean (config/credentials-available? creds))))
  {:num-tests 500})

(check! "credentials preserves the three documented keys"
  (prop/for-all [email field-value
                 password field-value
                 cookie field-value
                 junkkey (gen/elements ["EXTRA" "LINKEDIN_EMAIL "])]
    (let [from-env (config/credentials {"LINKEDIN_EMAIL" email
                                        "LINKEDIN_PASSWORD" password
                                        "LINKEDIN_COOKIE" cookie
                                        junkkey "ignored"})]
      (and (= #{:email :password :cookie} (set (keys from-env)))
           (= email (:email from-env))
           (= password (:password from-env))
           (= cookie (:cookie from-env)))))
  {:num-tests 300})
(ns linkedin-profile-api.config
  (:require [clojure.string :as str]))

(defn credentials
  "Read credential values from an environment map. Returns a map with keys
  :email, :password, and :cookie, each a string or nil."
  [env]
  {:email (get env "LINKEDIN_EMAIL")
   :password (get env "LINKEDIN_PASSWORD")
   :cookie (get env "LINKEDIN_COOKIE")})

(defn credentials-available?
  "True when the config carries enough to authenticate against LinkedIn:
  either a session cookie, or a non-blank email/password pair."
  [creds]
  (let [cookie (:cookie creds)]
    (boolean
      (or (and cookie (not (empty? cookie)))
          (and (not (str/blank? (:email creds)))
               (not (str/blank? (:password creds))))))))

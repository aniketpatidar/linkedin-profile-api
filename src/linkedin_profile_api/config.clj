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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:50.033606275+05:30", :module-hash "-1262240895", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "364430109"} {:id "defn/credentials", :kind "defn", :line 4, :end-line nil, :hash "1798150364"} {:id "defn/credentials-available?", :kind "defn", :line 12, :end-line nil, :hash "1726865286"}]}
;; clj-mutate-manifest-end

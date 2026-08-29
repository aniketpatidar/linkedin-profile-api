(ns linkedin-profile-api.profile-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.profile :as profile]))

(def fetched-at "2026-08-30T01:00:00Z")
(def url "https://www.linkedin.com/in/janedoe")

(def full-raw
  {:name "Jane Doe"
   :headline "Software Engineer at Acme"
   :about "I build things."
   :location "San Francisco, CA"
   :experience [{:title "Engineer" :company "Acme" :start 2018 :end 2024}]
   :education [{:school "MIT" :degree "B.S. Computer Science"}]
   :skills [{:name "Clojure"} {:name "Java"}]
   :certifications [{:name "AWS Certified" :authority "Amazon"}]
   :languages [{:name "English"} {:name "Spanish"}]
   :profile-images ["https://media.licdn.com/foo.jpg"]})

(describe "linkedin-profile-api.profile/build-profile"
  (it "echoes the url"
    (should= url (:url (profile/build-profile full-raw url fetched-at))))
  (it "sets the fetched-at timestamp"
    (should= fetched-at (:fetched_at (profile/build-profile full-raw url fetched-at))))
  (it "maps name and headline"
    (let [p (profile/build-profile full-raw url fetched-at)]
      (should= "Jane Doe" (:name p))
      (should= "Software Engineer at Acme" (:headline p))))
  (it "maps about and location"
    (let [p (profile/build-profile full-raw url fetched-at)]
      (should= "I build things." (:about p))
      (should= "San Francisco, CA" (:location p))))
  (it "maps experience items to title and company"
    (let [p (profile/build-profile full-raw url fetched-at)]
      (should= [{:title "Engineer" :company "Acme"}]
               (:experience p))))
  (it "maps education items to school and degree"
    (let [p (profile/build-profile full-raw url fetched-at)]
      (should= [{:school "MIT" :degree "B.S. Computer Science"}]
               (:education p))))
  (it "maps skills to name"
    (let [p (profile/build-profile full-raw url fetched-at)]
      (should= [{:name "Clojure"} {:name "Java"}] (:skills p))))
  (it "maps certifications to name and authority"
    (let [p (profile/build-profile full-raw url fetched-at)]
      (should= [{:name "AWS Certified" :authority "Amazon"}]
               (:certifications p))))
  (it "maps languages to name"
    (let [p (profile/build-profile full-raw url fetched-at)]
      (should= [{:name "English"} {:name "Spanish"}] (:languages p))))
  (it "passes profile images through"
    (let [p (profile/build-profile full-raw url fetched-at)]
      (should= ["https://media.licdn.com/foo.jpg"] (:profile_images p)))))

(describe "linkedin-profile-api.profile/build-profile optional fields"
  (it "omits about when absent"
    (let [p (profile/build-profile (dissoc full-raw :about) url fetched-at)]
      (should-not (contains? p :about))))
  (it "omits location when absent"
    (let [p (profile/build-profile (dissoc full-raw :location) url fetched-at)]
      (should-not (contains? p :location))))
  (it "omits certifications when absent or empty"
    (let [p1 (profile/build-profile (dissoc full-raw :certifications) url fetched-at)
          p2 (profile/build-profile (assoc full-raw :certifications []) url fetched-at)]
      (should-not (contains? p1 :certifications))
      (should-not (contains? p2 :certifications))))
  (it "omits languages when absent or empty"
    (let [p (profile/build-profile (dissoc full-raw :languages) url fetched-at)]
      (should-not (contains? p :languages))))
  (it "omits profile images when absent or empty"
    (let [p1 (profile/build-profile (dissoc full-raw :profile-images) url fetched-at)
          p2 (profile/build-profile (assoc full-raw :profile-images []) url fetched-at)]
      (should-not (contains? p1 :profile_images))
      (should-not (contains? p2 :profile_images))))
  (it "omits empty section arrays"
    (let [p (profile/build-profile (assoc full-raw :skills [] :education [] :experience []) url fetched-at)]
      (should-not (contains? p :skills))
      (should-not (contains? p :education))
      (should-not (contains? p :experience)))))

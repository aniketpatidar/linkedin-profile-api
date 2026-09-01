(ns linkedin-profile-api.voyager-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.voyager :as voyager]
            [clojure.string :as str]))

(def voyager-profile
  {:name "Jane Doe"
   :headline "Software Engineer at Acme"
   :summary "I build things."
   :locationName "San Francisco, CA"
   :positionView [{:title "Engineer" :companyName "Acme"}]
   :educationsView [{:schoolName "MIT" :degreeName "B.S. Computer Science"}]
   :skillViews [{:name "Clojure"} {:name "Java"}]
   :certificationView [{:name "AWS Certified" :authority "Amazon"}]
   :languageView [{:name "English"}]
   :displayPictureUrl "https://media.licdn.com/foo.jpg"})

(describe "linkedin-profile-api.voyager/normalize"
  (it "extracts name"
    (should= "Jane Doe" (:name (voyager/normalize voyager-profile))))
  (it "extracts headline"
    (should= "Software Engineer at Acme" (:headline (voyager/normalize voyager-profile))))
  (it "extracts about from summary"
    (should= "I build things." (:about (voyager/normalize voyager-profile))))
  (it "extracts location"
    (should= "San Francisco, CA" (:location (voyager/normalize voyager-profile))))
  (it "extracts experience items with title and company"
    (should= [{:title "Engineer" :company "Acme"}]
             (:experience (voyager/normalize voyager-profile))))
  (it "extracts education items with school and degree"
    (should= [{:school "MIT" :degree "B.S. Computer Science"}]
             (:education (voyager/normalize voyager-profile))))
  (it "extracts skills by name"
    (should= [{:name "Clojure"} {:name "Java"}]
             (:skills (voyager/normalize voyager-profile))))
  (it "extracts certifications with name and authority"
    (should= [{:name "AWS Certified" :authority "Amazon"}]
             (:certifications (voyager/normalize voyager-profile))))
  (it "extracts languages by name"
    (should= [{:name "English"}]
             (:languages (voyager/normalize voyager-profile))))
  (it "extracts profile images from the display picture url"
    (should= ["https://media.licdn.com/foo.jpg"]
             (:profile_images (voyager/normalize voyager-profile)))))

(describe "linkedin-profile-api.voyager/normalize tolerant shapes"
  (it "handles a nested :profile identity object"
    (let [raw {:profile {:firstName "Jane" :lastName "Doe"
                         :headline "Eng" :summary "About me" :locationName "NY"}
               :positionView []}]
      (let [n (voyager/normalize raw)]
        (should= "Jane Doe" (:name n))
        (should= "Eng" (:headline n))
        (should= "About me" (:about n))
        (should= "NY" (:location n)))))
  (it "handles company page nested experience with companyName"
    (let [n (voyager/normalize {:positionView [{:companyName "Acme"
                                                :title "Senior Engineer"}]})]
      (should= [{:title "Senior Engineer" :company "Acme"}] (:experience n))))
  (it "reads nested map values inside experience items"
    (let [n (voyager/normalize {:positionView [{:title {:text "Founder"}
                                                :company {:name "Acme"}}]})]
      (should= [{:title "Founder" :company "Acme"}] (:experience n))))
  (it "reads location from a nested :location map"
    (let [n (voyager/normalize {:location {:name "San Francisco"}})]
      (should= "San Francisco" (:location n))))
  (it "keeps an already-normalized scalar :location"
    (let [n (voyager/normalize {:location "NY"})]
      (should= "NY" (:location n))))
  (it "preserves already-normalized profile images"
    (let [n (voyager/normalize {:profile_images ["https://media.linkedin.com/a.jpg"
                                                 "https://media.linkedin.com/b.jpg"]})]
      (should= ["https://media.linkedin.com/a.jpg" "https://media.linkedin.com/b.jpg"]
               (:profile_images n))))
  (it "omits sections that are absent"
    (let [n (voyager/normalize {:name "Jane"})]
      (should-not (contains? n :skills))
      (should-not (contains? n :about))
      (should-not (contains? n :profile-images))))
  (it "returns an empty map for nil input"
    (should= {} (voyager/normalize nil)))
  (it "omits empty arrays in sections"
    (let [n (voyager/normalize {:name "Jane" :skillViews [] :positionView []})]
      (should-not (contains? n :skills))
      (should-not (contains? n :experience)))))

(ns linkedin-profile-api.profile
  (:require [clojure.string :as str]))

(defn- item-keys
  "Keep only the documented keys from a section item, dropping nil/empty values."
  [item keys]
  (into {}
        (keep (fn [k]
                (let [v (get item k)]
                  (when v [k v]))))
        keys))

(defn build-profile
  "Build the structured API profile from normalized raw profile data.

  Takes a map with optional keys :name, :headline, :about, :location,
  :experience, :education, :skills, :certifications, :languages,
  :profile-images. Section arrays contain maps with the documented item keys.
  Optional fields are omitted from the result when absent or empty so sparse
  profiles produce a clean response."
  [{:keys [name headline about location experience education skills
           certifications languages profile-images]}
   url fetched-at]
  (cond-> {:url url
           :fetched_at fetched-at}
    name (assoc :name name)
    headline (assoc :headline headline)
    about (assoc :about about)
    location (assoc :location location)
    (seq experience) (assoc :experience (map #(item-keys % [:title :company]) experience))
    (seq education) (assoc :education (map #(item-keys % [:school :degree]) education))
    (seq skills) (assoc :skills (map #(item-keys % [:name]) skills))
    (seq certifications) (assoc :certifications (map #(item-keys % [:name :authority]) certifications))
    (seq languages) (assoc :languages (map #(item-keys % [:name]) languages))
    (seq profile-images) (assoc :profile_images profile-images)))

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

(defn- assoc-when
  "Assoc k -> v when v is truthy."
  [m k v]
  (if v (assoc m k v) m))

(defn- assoc-when-seq
  "Assoc k -> (map f items) when items is non-empty."
  [m k items f]
  (if (seq items)
    (assoc m k (map f items))
    m))

(defn- assoc-when-items
  "Assoc k -> items (as-is) when items is non-empty."
  [m k items]
  (if (seq items) (assoc m k items) m))

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
  (-> {:url url
       :fetched_at fetched-at}
      (assoc-when :name name)
      (assoc-when :headline headline)
      (assoc-when :about about)
      (assoc-when :location location)
      (assoc-when-seq :experience experience #(item-keys % [:title :company]))
      (assoc-when-seq :education education #(item-keys % [:school :degree]))
      (assoc-when-seq :skills skills #(item-keys % [:name]))
      (assoc-when-seq :certifications certifications #(item-keys % [:name :authority]))
      (assoc-when-seq :languages languages #(item-keys % [:name]))
      (assoc-when-items :profile_images profile-images)))

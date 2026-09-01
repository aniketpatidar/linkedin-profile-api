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
  "Assoc k -> (vec (map f items)) when items is non-empty."
  [m k items f]
  (if (seq items)
    (assoc m k (vec (map f items)))
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
           certifications languages profile_images]}
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
      (assoc-when-items :profile_images profile_images)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:52.206492133+05:30", :module-hash "-1417496546", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-681925160"} {:id "defn-/item-keys", :kind "defn-", :line 4, :end-line nil, :hash "1542667306"} {:id "defn-/assoc-when", :kind "defn-", :line 13, :end-line nil, :hash "835750399"} {:id "defn-/assoc-when-seq", :kind "defn-", :line 18, :end-line nil, :hash "1980836103"} {:id "defn-/assoc-when-items", :kind "defn-", :line 25, :end-line nil, :hash "-784132910"} {:id "defn/build-profile", :kind "defn", :line 30, :end-line nil, :hash "134813223"}]}
;; clj-mutate-manifest-end

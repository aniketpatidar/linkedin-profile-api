(ns linkedin-profile-api.profile-shape-property
  (:require [property.runner :refer [check!]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [linkedin-profile-api.voyager :as voyager]
            [linkedin-profile-api.profile :as profile]))

(def url "https://www.linkedin.com/in/janedoe")
(def fetched-at "2026-08-30T01:00:00Z")

(defn- truncate [s]
  (if (> (count s) 40) (subs s 0 40) (str s)))

(def text-str
  (gen/fmap truncate (gen/one-of [gen/string-alphanumeric
                                  gen/string])))

(def maybe-str (gen/one-of [(gen/return nil) text-str]))

(def nested-name
  (gen/hash-map :name text-str :localizedName text-str))

(def section-item-gen
  (gen/hash-map
    :title maybe-str
    :companyName maybe-str
    :company nested-name
    :schoolName maybe-str
    :school nested-name
    :degreeName maybe-str
    :degree nested-name
    :name maybe-str
    :authority nested-name
    :skill nested-name
    :language nested-name
    :profilePicture (gen/hash-map :displayPictureUrl maybe-str)
    :decoy (gen/hash-map :lowLevelField maybe-str :url maybe-str)))

(def section-gen (gen/vector section-item-gen 0 8))

(def raw-gen
  (gen/hash-map
    :firstName maybe-str
    :lastName maybe-str
    :name maybe-str
    :displayName maybe-str
    :headline maybe-str
    :headlineText maybe-str
    :summary maybe-str
    :about maybe-str
    :location maybe-str
    :locationName maybe-str
    :positionView section-gen
    :educationsView section-gen
    :skillViews section-gen
    :certificationView section-gen
    :languageView section-gen
    :displayPictureUrl maybe-str
    :profilePicture (gen/hash-map :displayPictureUrl maybe-str)
    :profile (gen/hash-map :firstName maybe-str :lastName maybe-str
                           :headline maybe-str :summary maybe-str
                           :locationName maybe-str)
    :decoy (gen/hash-map :lowLevelField maybe-str :other (gen/vector maybe-str 0 8))))

(def documented-top-keys
  #{:url :fetched_at :name :headline :about :location
    :experience :education :skills :certifications :languages :profile_images})

(def section-item-keys
  {:experience #{:title :company}
   :education #{:school :degree}
   :skills #{:name}
   :certifications #{:name :authority}
   :languages #{:name}})

(defn- item-shape-ok? [allowed item]
  (and (map? item)
       (every? (partial contains? allowed) (keys item))
       (every? string? (vals item))))

(defn closed-shape? [p]
  (and (every? documented-top-keys (keys p))
       (string? (:url p))
       (string? (:fetched_at p))
       (every? (fn [k] (or (nil? (get p k)) (string? (get p k))))
               [:name :headline :about :location])
       (every? (fn [k]
                 (let [items (get p k)]
                   (or (nil? items)
                       (and (vector? items)
                            (every? #(item-shape-ok? (section-item-keys k) %) items)))))
               [:experience :education :skills :certifications :languages])
       (or (nil? (:profile_images p))
           (and (vector? (:profile_images p))
                (every? string? (:profile_images p))))))

(defn sparse-ok? [p]
  (and (every? (fn [k]
                 (or (not (contains? p k))
                     (and (string? (get p k))
                          (not (str/blank? (get p k))))))
               [:about :location :name :headline])
       (every? (fn [k]
                 (or (not (contains? p k))
                     (let [items (get p k)]
                       (and (seq items)
                            (every? (fn [item]
                                      (and (map? item)
                                           (seq (keys item))
                                           (every? (fn [v] (and (string? v)
                                                                (not (str/blank? v))))
                                                   (vals item))))
                                    items)))))
               [:experience :education :skills :certifications :languages])
       (or (not (contains? p :profile_images))
           (let [imgs (get p :profile_images)]
             (and (seq imgs)
                  (every? (fn [v] (and (string? v) (not (str/blank? v)))) imgs))))))

(check! "voyager/normalize leaks no undocumented fields into the API response"
  (prop/for-all [raw raw-gen]
    (let [p (profile/build-profile (voyager/normalize raw) url fetched-at)]
      (closed-shape? p)))
  {:num-tests 500})

(check! "voyager/normalize is idempotent"
  (prop/for-all [raw raw-gen]
    (= (voyager/normalize raw)
       (voyager/normalize (voyager/normalize raw))))
  {:num-tests 500})

(check! "profile/build-profile is a stable round trip"
  (prop/for-all [raw raw-gen]
    (let [n (voyager/normalize raw)
          p (profile/build-profile n url fetched-at)]
      (= p (profile/build-profile p url fetched-at))))
  {:num-tests 500})

(check! "sparse profiles omit empty and blank fields"
  (prop/for-all [raw raw-gen]
    (sparse-ok? (profile/build-profile (voyager/normalize raw) url fetched-at)))
  {:num-tests 300})
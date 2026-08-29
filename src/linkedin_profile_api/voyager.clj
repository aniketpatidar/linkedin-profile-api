(ns linkedin-profile-api.voyager
  (:require [clojure.string :as str]))

(defn- candidates [raw]
  (if (map? (:profile raw)) [raw (:profile raw)] [raw]))

(defn- probe-key
  "Return the first present, non-empty value of any key in ks from the first
  candidate map carrying it."
  [maps ks]
  (some (fn [m]
          (some (fn [k]
                  (let [v (get m k)]
                    (when (and v (not (coll? v)) (seq (str/trim (str v)))) v)))
                ks))
        maps))

(defn- section
  "Return the vector of items for the first present array under any key in ks,
  across the candidate maps, or nil."
  [maps ks]
  (some (fn [m]
          (some (fn [k]
                  (let [v (get m k)]
                    (when (and (coll? v) (seq v)) v)))
                ks))
        maps))

(defn- nested-value
  "Read key k from exts nested maps within item, preferring a scalar leaf."
  [item k exts]
  (or (probe-key [item] [k])
      (let [v (get item k)]
        (when (map? v)
          (probe-key (conj [item] v) exts)))))

(defn extract-name [raw]
  (let [maps (candidates raw)]
    (or (probe-key maps [:name :displayName])
        (let [f (probe-key maps [:firstName :givenName])
              l (probe-key maps [:lastName :familyName])]
          (when (or f l) (str/trim (str f " " l)))))))

(defn extract-headline [raw]
  (probe-key (candidates raw) [:headline :headlineText]))

(defn extract-about [raw]
  (probe-key (candidates raw) [:summary :about]))

(defn extract-location [raw]
  (let [maps (conj (candidates raw) (or (:location raw) (:location (:profile raw))))]
    (probe-key maps [:locationName :locationNameText :name :localizedName])))

(defn extract-experience [raw]
  (seq (keep (fn [it]
               (let [title (or (nested-value it :title [:text :value :name])
                               (probe-key [it] [:name]))
                     company (or (nested-value it :company [:name :localizedName :text])
                                 (probe-key [it] [:companyName]))]
                 (when (or title company) {:title title :company company})))
             (section (candidates raw) [:positionView :positions :experience]))))

(defn extract-education [raw]
  (seq (keep (fn [it]
               (let [school (or (nested-value it :school [:name :localizedName])
                                (probe-key [it] [:schoolName]))
                     degree (or (nested-value it :degree [:name :localizedName])
                                (probe-key [it] [:degreeName]))]
                 (when (or school degree) {:school school :degree degree})))
             (section (candidates raw) [:educationsView :education :educations]))))

(defn extract-skills [raw]
  (seq (keep (fn [it]
               (let [name (or (nested-value it :name [:name :localizedName :text])
                              (nested-value it :skill [:name :localizedName :text]))]
                 (when name {:name name})))
             (section (candidates raw) [:skillViews :skills :profileSkill :skillView]))))

(defn extract-certifications [raw]
  (seq (keep (fn [it]
               (let [name (or (nested-value it :name [:name :localizedName])
                              (nested-value it :certification [:name :localizedName]))
                     authority (nested-value it :authority [:name :localizedName])]
                 (when (or name authority)
                   (cond-> {:name name} authority (assoc :authority authority)))))
             (section (candidates raw) [:certificationView :certifications :certification]))))

(defn extract-languages [raw]
  (seq (keep (fn [it]
               (let [name (or (nested-value it :name [:name :localizedName])
                              (nested-value it :language [:name :localizedName]))]
                 (when name {:name name})))
             (section (candidates raw) [:languageView :languages :language]))))

(defn extract-profile-images [raw]
  (let [maps (conj (candidates raw) (:profilePicture raw))]
    (let [url (probe-key maps [:displayPictureUrl :pictureUrl :profilePictureUrl :url])]
      (when (and url (seq (str/trim url)))
        [(str/trim url)]))))

(defn normalize
  "Turn a raw LinkedIn (Voyager) profile response into the normalized raw profile
  map consumed by profile/build-profile. Optional fields are omitted when the
  source data is absent, so sparse profiles stay sparse."
  [raw]
  (if-not (map? raw)
    {}
    (let [name (extract-name raw)
          headline (extract-headline raw)
          about (extract-about raw)
          location (extract-location raw)
          experience (extract-experience raw)
          education (extract-education raw)
          skills (extract-skills raw)
          certifications (extract-certifications raw)
          languages (extract-languages raw)
          images (extract-profile-images raw)]
      (cond-> {}
        (seq (str/trim (str name))) (assoc :name (str/trim (str name)))
        (seq (str/trim (str headline))) (assoc :headline (str/trim (str headline)))
        (seq (str/trim (str about))) (assoc :about (str/trim (str about)))
        (seq (str/trim (str location))) (assoc :location (str/trim (str location)))
        (seq experience) (assoc :experience experience)
        (seq education) (assoc :education education)
        (seq skills) (assoc :skills skills)
        (seq certifications) (assoc :certifications certifications)
        (seq languages) (assoc :languages languages)
        (seq images) (assoc :profile-images images)))))

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

(defn- present-scalar
  "Return the trimmed string value when v is a non-blank scalar, else nil."
  [v]
  (when (and v (not (coll? v)))
    (let [t (str/trim (str v))]
      (when (seq t) t))))

(defn- assoc-scalar
  "Assoc k -> trimmed v when v is a non-blank scalar."
  [m k v]
  (if-let [t (present-scalar v)]
    (assoc m k t)
    m))

(defn- assoc-items
  "Assoc k -> v when v is a non-empty collection."
  [m k v]
  (if (seq v) (assoc m k v) m))

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
  (let [loc (or (:location raw) (:location (:profile raw)))
        from-profile (probe-key (candidates raw)
                                [:locationName :locationNameText :localizedName])
        from-loc (when (map? loc)
                   (probe-key [loc] [:name :localizedName]))
        already-normalized (when (and (contains? raw :location)
                                      (not (coll? (:location raw))))
                             (present-scalar (:location raw)))]
    (or from-profile from-loc already-normalized)))

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

(defn- normalized-images
  "Return the images already normalized under :profile_images/:profile-images
  as a vector of trimmed strings, so re-normalizing a normalized profile is
  stable."
  [raw]
  (let [v (or (:profile_images raw) (:profile-images raw))]
    (when (and (coll? v) (seq v))
      (vec (map (comp str/trim str) v)))))

(defn extract-profile-images [raw]
  (let [maps (conj (candidates raw) (:profilePicture raw))
        url (probe-key maps [:displayPictureUrl :pictureUrl :profilePictureUrl :url])]
    (or (when (and url (seq (str/trim url)))
          [(str/trim url)])
        (normalized-images raw))))

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
      (-> {}
          (assoc-scalar :name name)
          (assoc-scalar :headline headline)
          (assoc-scalar :about about)
          (assoc-scalar :location location)
          (assoc-items :experience experience)
          (assoc-items :education education)
          (assoc-items :skills skills)
          (assoc-items :certifications certifications)
          (assoc-items :languages languages)
          (assoc-items :profile_images images)))))

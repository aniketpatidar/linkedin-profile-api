(ns linkedin-profile-api.url-property
  (:require [property.runner :refer [check!]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [linkedin-profile-api.url :as url]))

(def non-blank
  (gen/such-that (comp not str/blank?) gen/string-alphanumeric))

(def public-id-gen
  (gen/one-of [non-blank
               (gen/fmap (fn [[a b]] (str a "-" b))
                         (gen/tuple non-blank non-blank))
               (gen/fmap (fn [[a b]] (str a "_" b))
                         (gen/tuple non-blank non-blank))]))

(def host-gen (gen/elements ["www.linkedin.com" "linkedin.com"]))

(defn valid-url [id host]
  (str "https://" host "/in/" id))

(check! "a valid profile url round-trips its public id"
  (prop/for-all [id public-id-gen
                 host host-gen]
    (let [s (valid-url id host)]
      (and (url/valid-profile-url? s)
           (= id (url/extract-public-id s)))))
  {:num-tests 500})

(check! "valid-profile-url? tolerates trailing slashes"
  (prop/for-all [id public-id-gen]
    (let [s (valid-url id "www.linkedin.com")]
      (and (url/valid-profile-url? s)
           (url/valid-profile-url? (str s "/"))
           (= id (url/extract-public-id (str s "/"))))))
  {:num-tests 300})

(check! "invalid urls never extract a public id"
  (prop/for-all [bad (gen/one-of [gen/string
                                  (gen/fmap #(str "https://example.com/in/" %)
                                            non-blank)
                                  (gen/fmap #(str "http://www.linkedin.com/in/" %)
                                            non-blank)
                                  (gen/fmap #(str "https://www.linkedin.com/company/" %)
                                            non-blank)])]
    (or (url/valid-profile-url? bad)
        (nil? (url/extract-public-id bad))))
  {:num-tests 500})

(check! "parse-url round-trips scheme, host, path and query"
  (prop/for-all [m (gen/hash-map
                     :scheme (gen/one-of [(gen/return "https") (gen/return "http")])
                     :host host-gen
                     :path (gen/fmap #(if (str/starts-with? % "/") % (str "/" %))
                                     gen/string-alphanumeric)
                     :query (gen/one-of [(gen/return "") (gen/return "?a=1&b=2")]))]
    (let [s (str (:scheme m) "://" (:host m) (:path m) (:query m))
          p (url/parse-url s)]
      (and (some? p)
           (= (:scheme m) (:scheme p))
           (= (:host m) (:host p))
           (= (:path m) (:path p))
           (= (:query m) (:query p)))))
  {:num-tests 300})
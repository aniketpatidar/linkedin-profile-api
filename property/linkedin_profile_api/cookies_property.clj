(ns linkedin-profile-api.cookies-property
  (:require [property.runner :refer [check!]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [linkedin-profile-api.cookies :as cookies]))

(def cookie-value
  "A generator of cookie values that cannot break header parsing: strip the
  header delimiters (semicolon, comma, quote) and guarantee non-blankness."
  (gen/fmap (fn [s]
              (let [clean (-> (str s)
                              (str/replace #"[;,\"]" "x"))]
                (if (str/blank? clean) "val" clean)))
            gen/string))

(check! "extract-li-at parses li_at=value from any set-cookie header"
  (prop/for-all [v cookie-value]
    (let [header (str "li_at=" v "; Domain=.linkedin.com; Path=/; HttpOnly")]
      (= v (cookies/extract-li-at header))))
  {:num-tests 300})

(check! "extract-li-at is stable across attribute order and casing"
  (prop/for-all [v cookie-value
                 kw (gen/elements ["Path=/;" "Secure;" "HttpOnly;"
                                   "DOMAIN=.linkedin.com; Path=/;"])]
    (let [header (str "li_at=" v "; " kw " Expires=Thu, 01 Jan 2026 00:00:00 GMT")]
      (= v (cookies/extract-li-at header))))
  {:num-tests 300})

(check! "extract-li-at never throws and yields a string or nil"
  (prop/for-all [s gen/string]
    (let [r (cookies/extract-li-at s)]
      (or (nil? r) (string? r))))
  {:num-tests 500})

(check! "extract-li-at maps a cookie cluster to the li_at entry"
  (prop/for-all [v cookie-value
                 others (gen/vector cookie-value 0 5)]
    (let [cluster (conj (mapv #(str "JSESSIONID=" %) others) (str "li_at=" v))]
      (= v (cookies/extract-li-at cluster))))
  {:num-tests 300})
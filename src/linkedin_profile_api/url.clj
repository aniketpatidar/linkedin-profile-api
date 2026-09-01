(ns linkedin-profile-api.url
  (:require [clojure.string :as str]))

(def valid-schemes #{"https"})
(def valid-hosts #{"linkedin.com" "www.linkedin.com"})
(def public-id-pattern #"^[A-Za-z0-9_-]+$")

(def ^:private url-pattern
  #"^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/([^/?#]+)([^?#]*)(\?[^#]*)?(#.*)?$")

(defn- url-map
  "Turn a url regex match into the parsed map."
  [[_ scheme host path query _anchor]]
  {:scheme (str/lower-case scheme)
   :host (str/lower-case host)
   :path (or path "")
   :query (or query "")})

(defn parse-url
  "Parse a url string into a map with :scheme, :host, :path, :query, or nil."
  [s]
  (when (and s (string? s))
    (try
      (let [m (re-matches url-pattern (str/trim s))]
        (when m (url-map m)))
      (catch Exception _ nil))))

(defn profile-path-segments
  "Return the non-empty path segments of a profile url path, or nil if not
  shaped like a /in/<public-id> profile."
  [url]
  (let [parsed (parse-url url)]
    (when (and parsed
               (contains? valid-schemes (:scheme parsed))
               (contains? valid-hosts (:host parsed)))
      (let [segments (remove empty? (str/split (:path parsed) #"/"))]
        (when (= "in" (first segments))
          segments)))))

(defn valid-profile-url?
  "True when the url is a LinkedIn profile url of the form
  https://(.)?linkedin.com/in/<public-id>."
  [s]
  (let [segments (and s (profile-path-segments s))]
    (boolean
      (and segments
           (= 2 (count segments))
           (re-matches public-id-pattern (second segments))))))

(defn extract-public-id
  "Return the public id segment of a valid profile url, else nil."
  [s]
  (let [segments (and s (profile-path-segments s))]
    (when (and segments (= 2 (count segments)))
      (second segments))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:53.235149411+05:30", :module-hash "-1147359255", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "63035352"} {:id "def/valid-schemes", :kind "def", :line 4, :end-line nil, :hash "781210529"} {:id "def/valid-hosts", :kind "def", :line 5, :end-line nil, :hash "380222084"} {:id "def/public-id-pattern", :kind "def", :line 6, :end-line nil, :hash "-1764611907"} {:id "def/url-pattern", :kind "def", :line 8, :end-line nil, :hash "1315818652"} {:id "defn-/url-map", :kind "defn-", :line 11, :end-line nil, :hash "1476991407"} {:id "defn/parse-url", :kind "defn", :line 19, :end-line nil, :hash "-150301354"} {:id "defn/profile-path-segments", :kind "defn", :line 28, :end-line nil, :hash "1370647180"} {:id "defn/valid-profile-url?", :kind "defn", :line 40, :end-line nil, :hash "-1741021232"} {:id "defn/extract-public-id", :kind "defn", :line 50, :end-line nil, :hash "-723203585"}]}
;; clj-mutate-manifest-end

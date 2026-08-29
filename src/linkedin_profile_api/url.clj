(ns linkedin-profile-api.url
  (:require [clojure.string :as str]))

(def valid-schemes #{"https"})
(def valid-hosts #{"linkedin.com" "www.linkedin.com"})
(def public-id-pattern #"^[A-Za-z0-9_-]+$")

(defn parse-url
  "Parse a url string into a map with :scheme, :host, :path, :query, or nil."
  [s]
  (when (and s (string? s))
    (try
      (let [m (re-matches #"^([a-zA-Z][a-zA-Z0-9+.-]*):\/\/([^/?#]+)([^?#]*)(\?[^#]*)?(#.*)?$" (str/trim s))]
        (when m
          (let [[_ scheme host path query _anchor] m
                path (or path "")]
            {:scheme (str/lower-case scheme)
             :host (str/lower-case host)
             :path path
             :query (or query "")})))
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

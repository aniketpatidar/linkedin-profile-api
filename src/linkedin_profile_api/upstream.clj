(ns linkedin-profile-api.upstream
  "IO-near adapter that wires the real LinkedIn HTTP transport to the core
  modules. Owns the Voyager request/response transport and injects
  babashka.http-client calls into the pure session logic in
  linkedin-profile-api.cookies."
  (:require [linkedin-profile-api.voyager :as voyager]
            [linkedin-profile-api.gateway :as gateway]
            [linkedin-profile-api.cookies :as cookies]
            [cheshire.core :as json]
            [clojure.string :as str]
            [babashka.http-client :as http]))

(defn encode-json [x] (json/generate-string x))

(defn decode-json [s] (json/parse-string s true))

(defn voyager-profile-url [public-id]
  (str "https://www.linkedin.com/voyager/api/identity/dash/profiles?q=memberIdentity&memberIdentity=" public-id
       "&decorationId=com.linkedin.voyager.dash.deco.identity.profile.FullProfileWithEntities-93"))

(defn- request-headers
  "Build the request headers for a Voyager call, using the session cookie."
  [config]
  (let [cookie (:cookie config)
        csrf (:csrf-token config)
        cookie-header (str "li_at=" cookie
                           (when csrf (str "; JSESSIONID=\"" csrf "\"")))]
    (cond-> {"User-Agent" "Mozilla/5.0 (compatible; LinkedInProfileAPI/1.0)"
             "Accept" "application/json"
             "x-restli-protocol-version" "2.0.0"
             "Cookie" cookie-header}
      csrf
      (assoc "csrf-token" csrf))))

(defn- status? [response status]
  (and (map? response) (= status (:status response))))

(defn- parse-voyager-body
  "Parse the response body as Voyager JSON, defaulting to {} when absent."
  [body]
  (try (decode-json body)
       (catch Exception _ nil)))

(defn- classify-response
  "Turn an upstream HTTP response (or thrown exception) into a fetch result."
  [response]
  (cond
    (status? response 200)
    (gateway/ok-result
      (voyager/normalize (or (parse-voyager-body (:body response)) {})))

    (status? response 404)
    (gateway/error-result :profile_not_found
                          "The requested LinkedIn profile was not found or is not viewable with the configured credentials.")

    :else
    (gateway/error-result :upstream_error
                          "The upstream LinkedIn request failed.")))

(defn fetch-profile
  "Fetch and normalize a LinkedIn profile by public id.

  Returns either {:status :ok :profile <normalized-raw>} or
  {:status :error :code <code> :message <msg>} where code is :profile_not_found
  or :upstream_error. Accepts an injectable :http-get (defaults to the real
  babashka HTTP client) so the classification logic is testable."
  [{:keys [public-id config http-get]
    :or {http-get (fn [url opts] (http/get url opts))}}]
  (try
    (let [csrf (or (when-let [j (:jsessionid config)]
                     (when (not (str/blank? j)) j))
                   (cookies/warmup-session http-get (:cookie config)))
          headers (request-headers (assoc config :csrf-token csrf))
          url (voyager-profile-url public-id)
          response (http-get url {:headers headers :as :string
                                  :throw false :timeout 20000})]
      (classify-response response))
    (catch Exception e
      (gateway/error-result :upstream_error
                            (str "The upstream LinkedIn request failed: " (.getMessage e))))))

(defn ensure-cookie
  "IO adapter for session resolution: binds the real babashka HTTP client into
  the pure login logic in linkedin-profile-api.cookies and delegates to it.
  Returns {:cookie <value>} or {:status :error :code :upstream_error ...}."
  [config & [opts]]
  (cookies/ensure-cookie
    config
    (merge {:http-get (fn [url o] (http/get url o))
            :http-post (fn [url o] (http/post url o))}
           opts)))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:52.905716815+05:30", :module-hash "-1673251582", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-281177053"} {:id "defn/encode-json", :kind "defn", :line 12, :end-line nil, :hash "-811703144"} {:id "defn/decode-json", :kind "defn", :line 14, :end-line nil, :hash "323789218"} {:id "defn/voyager-profile-url", :kind "defn", :line 16, :end-line nil, :hash "498763535"} {:id "defn-/request-headers", :kind "defn-", :line 19, :end-line nil, :hash "540305641"} {:id "defn-/status?", :kind "defn-", :line 33, :end-line nil, :hash "-561858485"} {:id "defn-/parse-voyager-body", :kind "defn-", :line 36, :end-line nil, :hash "1126226643"} {:id "defn-/classify-response", :kind "defn-", :line 42, :end-line nil, :hash "-324834385"} {:id "defn/fetch-profile", :kind "defn", :line 58, :end-line nil, :hash "-192866336"} {:id "defn/ensure-cookie", :kind "defn", :line 78, :end-line nil, :hash "-209402762"}]}
;; clj-mutate-manifest-end

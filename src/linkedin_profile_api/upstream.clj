(ns linkedin-profile-api.upstream
  "IO-near adapter that wires the real LinkedIn HTTP transport to the core
  modules. Owns the Voyager request/response transport and injects
  babashka.http-client calls into the pure session logic in
  linkedin-profile-api.cookies."
  (:require [linkedin-profile-api.voyager :as voyager]
            [linkedin-profile-api.gateway :as gateway]
            [linkedin-profile-api.cookies :as cookies]
            [cheshire.core :as json]
            [babashka.http-client :as http]))

(defn encode-json [x] (json/generate-string x))

(defn decode-json [s] (json/parse-string s true))

(defn voyager-profile-url [public-id]
  (str "https://www.linkedin.com/voyager/api/identity/profiles/" public-id))

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
    (let [csrf (cookies/warmup-session http-get)
          headers (request-headers (assoc config :csrf-token csrf))
          url (voyager-profile-url public-id)
          response (http-get url {:headers headers :as :string
                                  :throw false :timeout 20000})]
      (classify-response response))
    (catch Exception e
      (gateway/error-result :upstream_error
                            (str "The upstream LinkedIn request failed: " (.getMessage e))))))

(ns linkedin-profile-api.server
  (:require [linkedin-profile-api.url :as url]
            [linkedin-profile-api.config :as config]
            [linkedin-profile-api.gateway :as gateway]
            [linkedin-profile-api.upstream :as upstream]
            [linkedin-profile-api.profile :as profile]
            [linkedin-profile-api.errors :as errors]
            [org.httpkit.server :as hk]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn- decode-pair
  "Decode a single k=v pair, skipping blank names."
  [kv]
  (let [[k v] (str/split kv #"=" 2)]
    (when (seq k)
      [(java.net.URLDecoder/decode k "UTF-8")
       (some-> v (java.net.URLDecoder/decode "UTF-8"))])))

(defn parse-query
  "Parse a query string into a map of (first) param name -> value."
  [qs]
  (when (and qs (seq qs))
    (->> (str/split (if (str/starts-with? qs "?") (subs qs 1) qs) #"&")
         (keep decode-pair)
         (into {}))))

(defn- json-response [status body]
  {:status status
   :headers {"Content-Type" "application/json"}
   :body (json/generate-string body)})

(defn- ok-response [body] (json-response 200 body))

(defn- error-response [code message]
  (json-response (errors/status-for-code code)
                 (errors/error-response code message)))

(defn- default-now []
  (str (java.time.Instant/now)))

(defn- default-env [] (into {} (System/getenv)))

(defn- blank-url? [url-value]
  (or (nil? url-value) (= "" url-value)))

(defn- invalid-url-response []
  (error-response :invalid_url (get errors/default-message :invalid_url)))

(defn- invalid-url? [url-value]
  (not (url/valid-profile-url? url-value)))

(defn- missing-credentials-response []
  (error-response :missing_credentials (get errors/default-message :missing_credentials)))

(defn- handle-session
  "Fetch the profile once a session cookie is present."
  [fetch-profile creds session url-value now]
  (let [public-id (url/extract-public-id url-value)
        result (fetch-profile {:public-id public-id
                               :config (assoc creds :cookie (gateway/session-cookie session))})]
    (cond
      (gateway/success? result)
      (ok-response (profile/build-profile (gateway/profile result) url-value (now)))

      (gateway/error? result)
      (error-response (gateway/code result) (gateway/message result))

      :else
      (error-response :upstream_error "The upstream LinkedIn request failed."))))

(defn- handle-session-result
  "Respond given the ensure-cookie result."
  [fetch-profile creds session url-value now]
  (if (gateway/error? session)
    (error-response :upstream_error "Could not establish a LinkedIn session.")
    (handle-session fetch-profile creds session url-value now)))

(defn- handle-profile
  "Route a /profile request. Uses injected deps for the environmental pieces so
  the routing and error taxonomy are unit-testable."
  [{:keys [env now ensure-cookie fetch-profile]
    :or {env (default-env)
         now default-now
         ensure-cookie upstream/ensure-cookie
         fetch-profile upstream/fetch-profile}}
   query-params]
  (let [url-value (get query-params "url")]
    (cond
      (blank-url? url-value) (invalid-url-response)
      (invalid-url? url-value) (invalid-url-response)
      :else
      (let [creds (config/credentials env)]
        (if-not (config/credentials-available? creds)
          (missing-credentials-response)
          (handle-session-result fetch-profile creds (ensure-cookie creds) url-value now))))))

(defn- get-route? [method uri path]
  (and (= :get method) (= path uri)))

(defn- not-found-response []
  (json-response (errors/status-for-code :not_found)
                 (errors/error-response :not_found)))

(defn handle-request
  "Top-level router. `req` is a ring-style request map with :request-method,
  :uri, and :query-string. `deps` injects environment and upstream behavior."
  [deps req]
  (let [method (or (:request-method req) :get)
        uri (or (:uri req) "/")
        query-params (parse-query (:query-string req))]
    (cond
      (get-route? method uri "/health")
      (ok-response {:status "ok"})

      (get-route? method uri "/profile")
      (handle-profile deps query-params)

      :else
      (not-found-response))))

(defn start
  "Start the HTTP server on the given port. The handler delegates to
  handle-request with defaults wired to the real implementation. Returns the
  running server."
  [port]
  (let [handler (fn [req] (handle-request {} req))]
    (hk/run-server handler {:port (int port)})))

(ns linkedin-profile-api.server
  (:require [linkedin-profile-api.url :as url]
            [linkedin-profile-api.config :as config]
            [linkedin-profile-api.upstream :as upstream]
            [linkedin-profile-api.profile :as profile]
            [linkedin-profile-api.errors :as errors]
            [org.httpkit.server :as hk]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn parse-query
  "Parse a query string into a map of (first) param name -> value."
  [qs]
  (when (and qs (seq qs))
    (->> (str/split (if (str/starts-with? qs "?") (subs qs 1) qs) #"&")
         (keep (fn [kv]
                 (let [[k v] (str/split kv #"=" 2)]
                   (when (seq k)
                     [(java.net.URLDecoder/decode k "UTF-8")
                      (some-> v (java.net.URLDecoder/decode "UTF-8"))]))))
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
      (or (nil? url-value) (= "" url-value))
      (error-response :invalid_url (get errors/default-message :invalid_url))

      (not (url/valid-profile-url? url-value))
      (error-response :invalid_url (get errors/default-message :invalid_url))

      :else
      (let [creds (config/credentials env)]
        (if-not (config/credentials-available? creds)
          (error-response :missing_credentials (get errors/default-message :missing_credentials))
          (let [session (ensure-cookie creds)]
            (if (= :error (:status session))
              (error-response :upstream_error "Could not establish a LinkedIn session.")
              (let [public-id (url/extract-public-id url-value)
                    result (fetch-profile {:public-id public-id
                                           :config (assoc creds :cookie (:cookie session))})]
                (case (:status result)
                  :ok (ok-response (profile/build-profile (:profile result) url-value (now)))
                  :error (error-response (:code result) (:message result))
                  (error-response :upstream_error "The upstream LinkedIn request failed."))))))))))

(defn handle-request
  "Top-level router. `req` is a ring-style request map with :request-method,
  :uri, and :query-string. `deps` injects environment and upstream behavior."
  [deps req]
  (let [method (or (:request-method req) :get)
        uri (or (:uri req) "/")
        query-params (parse-query (:query-string req))]
    (cond
      (and (= :get method) (= "/health" uri))
      (ok-response {:status "ok"})

      (and (= :get method) (= "/profile" uri))
      (handle-profile deps query-params)

      :else
      (json-response 404 (errors/error-response :not_found "Not found.")))))

(defn start
  "Start the HTTP server on the given port. The handler delegates to
  handle-request with defaults wired to the real implementation. Returns the
  running server."
  [port]
  (let [handler (fn [req] (handle-request {} req))]
    (hk/run-server handler {:port (int port)})))

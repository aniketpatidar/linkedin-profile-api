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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:52.572788159+05:30", :module-hash "1462935029", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-2144153123"} {:id "defn-/decode-pair", :kind "defn-", :line 12, :end-line nil, :hash "1616683861"} {:id "defn/parse-query", :kind "defn", :line 20, :end-line nil, :hash "1136360116"} {:id "defn-/json-response", :kind "defn-", :line 28, :end-line nil, :hash "406182706"} {:id "defn-/ok-response", :kind "defn-", :line 33, :end-line nil, :hash "-1455122563"} {:id "defn-/error-response", :kind "defn-", :line 35, :end-line nil, :hash "1348266850"} {:id "defn-/default-now", :kind "defn-", :line 39, :end-line nil, :hash "2113605110"} {:id "defn-/default-env", :kind "defn-", :line 42, :end-line nil, :hash "2051070100"} {:id "defn-/blank-url?", :kind "defn-", :line 44, :end-line nil, :hash "949412623"} {:id "defn-/invalid-url-response", :kind "defn-", :line 47, :end-line nil, :hash "741019498"} {:id "defn-/invalid-url?", :kind "defn-", :line 50, :end-line nil, :hash "1635351730"} {:id "defn-/missing-credentials-response", :kind "defn-", :line 53, :end-line nil, :hash "2108546527"} {:id "defn-/handle-session", :kind "defn-", :line 56, :end-line nil, :hash "-1208446625"} {:id "defn-/handle-session-result", :kind "defn-", :line 72, :end-line nil, :hash "1852358955"} {:id "defn-/handle-profile", :kind "defn-", :line 79, :end-line nil, :hash "-1256925379"} {:id "defn-/get-route?", :kind "defn-", :line 98, :end-line nil, :hash "-339617786"} {:id "defn-/not-found-response", :kind "defn-", :line 101, :end-line nil, :hash "-988321250"} {:id "defn/handle-request", :kind "defn", :line 105, :end-line nil, :hash "-776868245"} {:id "defn/start", :kind "defn", :line 122, :end-line nil, :hash "922959689"}]}
;; clj-mutate-manifest-end

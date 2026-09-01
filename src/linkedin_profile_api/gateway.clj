(ns linkedin-profile-api.gateway
  "Owns the narrow contract between the delivery layer and the LinkedIn
  adapter. The profile-fetch and session-cookie seams are injected by the
  server and implemented by the IO-near upstream adapter; the result shapes
  they exchange live here so the high-level modules never depend on adapter
  details and the adapter cannot let its shapes drift.")

(defn ok-result
  "Build a successful profile-fetch result carrying the normalized raw
  profile consumed by profile/build-profile."
  [profile]
  {:status :ok :profile profile})

(defn error-result
  "Build a failed result carrying an errors/status-by-code code and a
  human-readable message."
  [code message]
  {:status :error :code code :message message})

(defn success?
  "True when a gateway result is a successful fetch."
  [result]
  (and (map? result) (= :ok (:status result))))

(defn error?
  "True when a gateway result is an error result."
  [result]
  (and (map? result) (= :error (:status result))))

(defn profile
  "The normalized raw profile carried by a successful fetch result."
  [result]
  (:profile result))

(defn code
  "The error code carried by an error result."
  [result]
  (:code result))

(defn message
  "The human-readable message carried by an error result."
  [result]
  (:message result))

(defn session-cookie
  "The li_at session cookie carried by a session result, or nil."
  [session]
  (:cookie session))
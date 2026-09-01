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

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:51.769919837+05:30", :module-hash "-1486225410", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "-146127248"} {:id "defn/ok-result", :kind "defn", :line 8, :end-line nil, :hash "2034092893"} {:id "defn/error-result", :kind "defn", :line 14, :end-line nil, :hash "926266405"} {:id "defn/success?", :kind "defn", :line 20, :end-line nil, :hash "399391607"} {:id "defn/error?", :kind "defn", :line 25, :end-line nil, :hash "-1063582110"} {:id "defn/profile", :kind "defn", :line 30, :end-line nil, :hash "1449258972"} {:id "defn/code", :kind "defn", :line 35, :end-line nil, :hash "250410874"} {:id "defn/message", :kind "defn", :line 40, :end-line nil, :hash "1572513475"} {:id "defn/session-cookie", :kind "defn", :line 45, :end-line nil, :hash "-727225410"}]}
;; clj-mutate-manifest-end

(ns linkedin-profile-api.errors)

(def status-by-code
  {:invalid_url 400
   :not_found 404
   :profile_not_found 404
   :missing_credentials 503
   :upstream_error 502})

(def default-message
  {:invalid_url "The url is not a valid LinkedIn profile url."
   :missing_credentials "LinkedIn credentials are not configured."
   :not_found "Not found."
   :profile_not_found "The requested LinkedIn profile was not found."
   :upstream_error "The upstream LinkedIn request failed."})

(defn status-for-code
  "Return the HTTP status for an error code keyword."
  [code]
  (get status-by-code code 500))

(defn error-response
  "Build the error body map for an error code keyword and message."
  ([code] (error-response code (get default-message code "An error occurred.")))
  ([code message]
   {:error {:code (name code) :message message}}))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:51.227886813+05:30", :module-hash "1572566884", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "735336599"} {:id "def/status-by-code", :kind "def", :line 3, :end-line nil, :hash "-2115667360"} {:id "def/default-message", :kind "def", :line 10, :end-line nil, :hash "-622244959"} {:id "defn/status-for-code", :kind "defn", :line 17, :end-line nil, :hash "2131084197"} {:id "defn/error-response", :kind "defn", :line 22, :end-line nil, :hash "110490354"}]}
;; clj-mutate-manifest-end

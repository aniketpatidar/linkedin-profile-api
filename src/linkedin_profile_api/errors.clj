(ns linkedin-profile-api.errors)

(def status-by-code
  {:invalid_url 400
   :profile_not_found 404
   :missing_credentials 503
   :upstream_error 502})

(def default-message
  {:invalid_url "The url is not a valid LinkedIn profile url."
   :missing_credentials "LinkedIn credentials are not configured."
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

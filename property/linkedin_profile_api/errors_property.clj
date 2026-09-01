(ns linkedin-profile-api.errors-property
  (:require [property.runner :refer [check!]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.string :as str]
            [linkedin-profile-api.errors :as errors]))

(check! "status-for-code is consistent with the documented taxonomy"
  (prop/for-all [code (gen/elements (keys errors/status-by-code))]
    (let [expected (get errors/status-by-code code)]
      (and (= expected (errors/status-for-code code))
           (string? (get errors/default-message code))
           (not (str/blank? (get errors/default-message code))))))
  {:num-tests 50})

(check! "unknown codes fall back to 500 with a generic message"
  (prop/for-all [code (gen/elements [:unknown_code :other])]
    (let [resp (errors/error-response code)]
      (and (= 500 (errors/status-for-code code))
           (= "An error occurred." (get-in resp [:error :message])))))
  {:num-tests 50})

(check! "error-response always emits exactly the documented envelope"
  (prop/for-all [code (gen/elements (keys errors/status-by-code))]
    (let [resp (errors/error-response code)
          env (:error resp)]
      (and (= #{:error} (set (keys resp)))
           (= (name code) (:code env))
           (string? (:message env))
           (not (str/blank? (:message env))))))
  {:num-tests 100})

(check! "a supplied message overrides the default without changing the shape"
  (prop/for-all [code (gen/elements (keys errors/status-by-code))
                 msg gen/string]
    (let [env (:error (errors/error-response code msg))]
      (and (= msg (:message env))
           (= (name code) (:code env))
           (= #{:code :message} (set (keys env))))))
  {:num-tests 100})
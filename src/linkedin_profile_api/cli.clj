(ns linkedin-profile-api.cli
  (:require [linkedin-profile-api.server :as server]
            [clojure.string :as str]))

(defn parse-port
  "Parse the port from args like [--port 8787], defaulting to 8787."
  [args]
  (let [idx (.indexOf (vec args) "--port")]
    (if (and (>= idx 0) (< (inc idx) (count args)))
      (Integer/parseInt (nth args (inc idx)))
      8787)))

(defn- serve-blocking
  "Start the server on port and block until a shutdown signal arrives."
  [port]
  (println (str "LinkedIn Profile API listening on http://127.0.0.1:" port))
  (server/start port)
  (let [stop (promise)]
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. #(deliver stop true)))
    @stop))

(defn print-usage
  "Print the CLI usage line."
  []
  (println "Usage: linkedin-profile-api serve --port <port>"))

(defn exit
  "Exit the process with the given code (separate var so tests can stub it)."
  [code]
  (System/exit code))

(defn -main [& args]
  (case (first args)
    "serve" (serve-blocking (parse-port (vec (rest args))))
    "help" (do (print-usage) (exit 0))
    (do (print-usage) (exit 1))))

;; clj-mutate-manifest-begin
;; {:version 1, :tested-at "2026-09-01T16:03:49.490083482+05:30", :module-hash "2020660808", :forms [{:id "form/0/ns", :kind "ns", :line 1, :end-line nil, :hash "2032282648"} {:id "defn/parse-port", :kind "defn", :line 5, :end-line nil, :hash "1043452629"} {:id "defn-/serve-blocking", :kind "defn-", :line 13, :end-line nil, :hash "1775307682"} {:id "defn/print-usage", :kind "defn", :line 23, :end-line nil, :hash "-562731932"} {:id "defn/exit", :kind "defn", :line 28, :end-line nil, :hash "1171762229"} {:id "defn/-main", :kind "defn", :line 33, :end-line nil, :hash "889099132"}]}
;; clj-mutate-manifest-end

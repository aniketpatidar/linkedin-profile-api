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

(defn -main [& args]
  (case (first args)
    "serve" (serve-blocking (parse-port (vec (rest args))))
    "help" (do (println "Usage: linkedin-profile-api serve --port <port>")
               (System/exit 0))
    (do (println "Usage: linkedin-profile-api serve --port <port>")
        (System/exit 1))))

(ns runner
  (:require [babashka.fs :as fs]
            [speclj.core]
            [speclj.config :as config]
            [speclj.results :as results]
            [speclj.run.standard :as s]))

(defn spec-files []
  (->> (concat (fs/glob "spec" "*_spec.clj")
               (fs/glob "spec" "**/*_spec.clj"))
       (map str)
       distinct
       sort))

(defn -main [& _]
  (doseq [f (spec-files)]
    (load-file f))
  (s/run-specs)
  (let [runner (config/active-runner)
        failures (results/fail-count @(.-results runner))]
    (System/exit (if (pos? failures) 1 0))))

(-main)

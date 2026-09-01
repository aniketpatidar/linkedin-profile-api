(ns property.runner
  "Property test runner. Loads every property/*_property.clj file, runs the
  registered quick-check properties via clojure.test.check, reports the
  aggregate outcome, and exits non-zero when any property fails.
  Run with: bb property"
  (:require [babashka.fs :as fs]
            [clojure.test.check :as tc]))

(def results (atom []))

(defn check!
  "Register and run a property. `name` is a short human-readable label;
  `property` is a clojure.test.check property (prop/for-all). Optional opts
  map accepts :num-tests (default 200) and :seed."
  ([name property] (check! name property {}))
  ([name property opts]
   (let [num-tests (or (:num-tests opts) 200)
         seed (or (:seed opts) (rand-int Integer/MAX_VALUE))
         r (tc/quick-check num-tests property {:seed seed})]
     (swap! results conj (assoc r :name name))
     r)))

(defn property-files []
  (->> (concat (fs/glob "property" "*_property.clj")
               (fs/glob "property" "**/*_property.clj"))
       (map str)
       distinct
       sort))

(defn run-all []
  (let [fails (filter #(not (true? (:result %))) @results)]
    (doseq [r @results]
      (println (if (true? (:result r)) "PASS" "FAIL")
               (:name r)
               (str "(" (:num-tests r) " cases)")
               (when-not (true? (:result r))
                 (str " " (pr-str (:shrunk r))))))
    (println (str "\nProperty results: " (- (count @results) (count fails))
                  " passed, " (count fails) " failed"))
    (System/exit (if (seq fails) 1 0))))

(defn load-property-files []
  (doseq [f (property-files)]
    (load-file f)))

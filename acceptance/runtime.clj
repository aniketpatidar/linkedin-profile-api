(ns acceptance.runtime
  "Acceptance runtime: expands JSON IR scenarios (with string keys) into
  executions, prepends background steps, substitutes example placeholders, and
  routes each concrete step to a project step handler."
  (:require [clojure.string :as str]))

(defn substitute
  "Replace every <name> placeholder in text with its value from the example
  map. Throws when a placeholder has no value."
  [text example]
  (reduce (fn [t placeholder]
            (let [value (get example placeholder)]
              (when (nil? value)
                (throw (ex-info (str "Missing example value for <" placeholder "> in step: " text)
                                {:placeholder placeholder :example example})))
              (str/replace t (str "<" placeholder ">") value)))
          text
          (map second (re-seq #"<([A-Za-z0-9_]+)>" text))))

(defn- expand-executions
  "Expand a scenario into one execution per example row, or a single execution
  with an empty example when there are no examples."
  [scenario]
  (let [examples (get scenario "examples")]
    (if (seq examples)
      (map-indexed (fn [i example] {:name (str (get scenario "name") "/example_" (inc i))
                                    :example example})
                   examples)
      [{:name (str (get scenario "name") "/example_1") :example {}}])))

(defn- resolve-step
  "Resolve a step against an example, producing a concrete-text step."
  [step example]
  {:keyword (get step "keyword")
   :text (substitute (get step "text") example)})

(defn- find-handler
  "Return [regex handler] for the first registry entry whose regex fully
  matches the concrete step text, else nil."
  [registry text]
  (first (filter (fn [[pattern _]]
                   (re-matches pattern text))
                 registry)))

(defn- run-handler [[pattern handler] world step example]
  (handler {:world world
            :example example
            :text (:text step)
            :keyword (:keyword step)
            :groups (re-matches pattern (:text step))}))

(defn run-scenario
  "Run the background + scenario steps for one execution. Returns the final
  world or throws on the first failed or unmatched step."
  [{:keys [background registry]} steps world example]
  (reduce (fn [w step]
            (let [step (resolve-step step example)
                  entry (find-handler registry (:text step))]
              (when-not entry
                (throw (ex-info (str "No step handler for: " (:text step))
                                {:step-text (:text step)})))
              (run-handler entry w step example)))
          world
          steps))

(defn run-feature
  "Run a complete feature IR (string-keyed map parsed from JSON). `registry` is
  a vector of [regex handler]. One shared world (seeded by `new-world`, default
  {}) is threaded across all scenario executions so a single server serves the
  whole feature. Returns a vector of execution results, each {:execution ...
  :world ...} or {:execution ... :error <msg> :cause <ex>} on failure."
  [ir registry
   & [{:keys [new-world]
       :or {new-world (fn [] {})}}]]
  (let [background (vec (get ir "background"))]
    (loop [scenarios (get ir "scenarios")
           world (new-world)
           out []]
      (if-let [scenario (first scenarios)]
        (let [scenario-results
              (loop [executions (expand-executions scenario)
                     w world
                     exec-out []]
                (if-let [execution (first executions)]
                  (let [res (try
                              (let [final-w (run-scenario {:background background
                                                           :registry registry}
                                                          (concat background (get scenario "steps"))
                                                          w
                                                          (:example execution))]
                                {:execution execution :world final-w})
                              (catch Exception e
                                {:execution execution
                                 :error (.getMessage e)
                                 :cause e}))]
                    (recur (rest executions)
                           (if (contains? res :world) (:world res) w)
                           (conj exec-out res)))
                  exec-out))]
          (recur (rest scenarios) (:world (last scenario-results)) (conj out scenario-results)))
        out))))

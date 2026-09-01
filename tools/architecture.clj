(ns architecture
  "Lightweight automated architecture checks for the LinkedIn Profile API.

  Enforces the dependency rule and information-hiding boundaries statically by
  scanning the :require clauses of every source namespace:

    - dependency rule: IO-near modules (upstream/server/cli) may depend inward
      on pure core modules, but pure core modules must never depend on them;
    - no framework/IO leakage: pure core modules must not require transport,
      HTTP, JSON, or other IO-near libraries;
    - no import cycles among project namespaces.

  Run with: bb architecture"
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(def project-dir "src")

(def layer-of
  "Project namespace -> layer. Higher layers sit farther from IO."
  {:core '#{linkedin-profile-api.errors
            linkedin-profile-api.url
            linkedin-profile-api.config
            linkedin-profile-api.gateway
            linkedin-profile-api.profile
            linkedin-profile-api.voyager
            linkedin-profile-api.cookies}
   :io    '#{linkedin-profile-api.upstream
             linkedin-profile-api.server
             linkedin-profile-api.cli}})

(defn layer-index [ns-sym]
  (if (contains? (:core layer-of) ns-sym)
    :core
    :io))

(def forbidden-for-core
  "Transport/framework namespaces that must never appear in a core namespace."
  '#{org.httpkit.server
     cheshire.core
     babashka.http-client
     babashka.curl
     babashka.fs
     babashka.process
     clojure.java.io
     clojure.java.shell})

(def project-nses
  (set (concat (:core layer-of) (:io layer-of))))

(defn ns-name-of
  "Extract the namespace symbol from a file's source text."
  [text]
  (when-let [m (re-find #"\(\s*ns\s+([^\s\)]+)" text)]
    (symbol (second m))))

(defn require-region
  "Return the text between :require and its matching close paren, or nil."
  [text]
  (when-let [start (str/index-of text "(:require")]
    (loop [depth 0
           idx (+ start (count "(:require"))]
      (let [c (get text idx)]
        (cond
          (nil? c) nil
          (= c \() (recur (inc depth) (inc idx))
          (= c \)) (if (zero? depth)
                     (subs text start idx)
                     (recur (dec depth) (inc idx)))
          :else (recur depth (inc idx)))))))

(defn required-nses
  "Extract the sorted set of required namespace symbols from source text,
  keeping only names that look like namespaces (contain a dot) or are project
  namespaces, and discarding :refer targets."
  [text]
  (let [region (require-region text)]
    (when region
      (->> (re-seq #"[\[(]([a-zA-Z][a-zA-Z0-9.\-]*)" region)
           (map (comp symbol second))
           (remove #(re-find #"^clojure\." (name %)))
           (remove #(re-find #"^java\." (name %)))
           (remove #(contains? #{:require :refer :as :rename} %))
           (filter #(or (str/includes? (name %) ".")
                        (contains? project-nses %)))
           distinct
           sort))))

(defn source-files []
  (sort (map str (fs/glob project-dir "**/*.clj"))))

(defn ns-info
  "Map ns-symbol -> {:file <path> :requires #{...}} for every source file."
  []
  (into {}
        (keep (fn [file]
                (let [text (slurp file)
                      ns-sym (ns-name-of text)]
                  (when (and ns-sym (contains? project-nses ns-sym))
                    [ns-sym {:file file :requires (set (required-nses text))}])))
              (source-files))))

(defn check-dependency-rule
  "Pure core namespaces must not require IO-adapter namespaces."
  [info]
  (for [[ns-sym {:keys [requires]} ] (sort-by key info)
        :when (= :core (layer-index ns-sym))
        dep requires
        :when (contains? project-nses dep)
        :when (not= :core (layer-index dep))]
    (str "  CORE " ns-sym " requires IO-near " dep " (" (:file (get info ns-sym)) ")")))

(defn check-framework-leakage
  "Pure core namespaces must not require transport/framework libraries."
  [info]
  (for [[ns-sym {:keys [requires]} ] (sort-by key info)
        :when (= :core (layer-index ns-sym))
        dep requires
        :when (contains? forbidden-for-core dep)]
    (str "  CORE " ns-sym " requires framework/IO library " dep
         " (" (:file (get info ns-sym)) ")")))

(defn project-edge-adjacency
  "Namespace -> set of required namespaces that are project namespaces."
  [info]
  (into {} (map (fn [[k {:keys [requires]}]] [k (set (filter project-nses requires))])) info))

(defn first-cycle
  "Return one cycle [a b ... a] present in the project dependency graph, or nil."
  [adj]
  (letfn [(dfs [node path]
            (some (fn [dep]
                    (if-let [i (first (keep-indexed (fn [i n] (when (= dep n) i))
                                                    path))]
                      (conj (subvec path i) dep)
                      (dfs dep (conj path dep))))
                  (get adj node)))]
    (some (fn [n] (dfs n [n])) (keys adj))))

(defn check-import-cycles [info]
  (let [adj (project-edge-adjacency info)
        cycle (first-cycle adj)]
    (if cycle
      [(str "  IMPORT CYCLE " (str/join " -> " cycle))]
      [])))

(defn report []
  (let [info (ns-info)
        problems (concat (check-dependency-rule info)
                         (check-framework-leakage info)
                         (check-import-cycles info))]
    (doseq [[ns-sym {:keys [requires]}] (sort-by key info)]
      (println "  " ns-sym "(" (count requires) " requires)"))
    (println)
    (if (seq problems)
      (do (println "ARCHITECTURE VIOLATIONS")
          (doseq [p problems] (println p))
          (System/exit 1))
      (do (println "ARCHITECTURE OK")
          (System/exit 0)))))

(report)
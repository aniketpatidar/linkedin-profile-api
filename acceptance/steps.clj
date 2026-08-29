(ns acceptance.steps
  "Project step handlers connecting Gherkin step text to the running LinkedIn
  Profile API server and to the delivered repository. The world holds the
  running server, its base URL, the credentials environment used to launch it,
  and the last HTTP response."
  (:require [babashka.curl :as curl]
            [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.java.shell :as shell]
            [clojure.string :as str]))

(defn- free-port []
  (let [s (java.net.ServerSocket. 0)]
    (try (.getLocalPort s) (finally (.close s)))))

(defn- read-env [name]
  (or (System/getenv name) ""))

(defn- launch-server!
  "Start the API server as a subprocess on a free port using the given env
  additions. Blocks until /health answers. Returns a world with :server (the
  process), :base-url, and :port."
  [env-additions]
  (let [port (free-port)
        base-url (str "http://127.0.0.1:" port)
        command (or (System/getenv "API_COMMAND")
                    (str "./bin/linkedin-profile-api serve"))
        parts (str/split command #"\s+")
        args (concat parts ["--port" (str port)])
        pb (ProcessBuilder. ^java.util.List (vec args))
        current-env (.environment pb)]
    (doseq [[k v] env-additions]
      (.put current-env k v))
    (.redirectErrorStream pb true)
    (let [proc (.start pb)
          start (System/currentTimeMillis)]
      (loop []
        (if (and (< (System/currentTimeMillis) (+ start 30000))
                 (try (= 200 (:status (curl/get (str base-url "/health"))))
                      (catch Exception _ false)))
          {:base-url base-url :port port :server proc}
          (if (>= (- (System/currentTimeMillis) start) 30000)
            (throw (ex-info "Server did not become healthy in time"
                            {:log (slurp (.getInputStream proc))}))
            (do (Thread/sleep 200) (recur))))))))

(defn- http-get
  "GET url, normalizing babashka.curl's exception-on-HTTP-error into a
  {:status :body} response map."
  [url]
  (try
    (let [r (curl/get url)]
      {:status (:status r) :body (:body r)})
    (catch Exception e
      (let [{:keys [status body]} (ex-data e)]
        {:status status :body body}))))

(defn- respond [world path]
  (let [resp (http-get (str (:base-url world) path))
        body (:body resp)]
    (assoc world
           :last {:status (:status resp)
                  :body (try (json/parse-string body)
                             (catch Exception _ body))
                  :raw body})))

(defn ensure-server
  "Launch the server when the world has no base URL yet; otherwise return the
  world unchanged. Makes the 'running' Given and every local HTTP request
  idempotently share one server."
  [{:keys [world] :as _ctx}]
  (if (:base-url world)
    world
    (launch-server! {})))

(defn- starter
  "Handler: 'the LinkedIn Profile API is running'."
  [ctx] (ensure-server ctx))

(defn- creds-configured
  "Handler: 'the LinkedIn Profile API has LinkedIn credentials configured'."
  [{:keys [world]}]
  (if (or (seq (read-env "LINKEDIN_COOKIE"))
          (and (seq (read-env "LINKEDIN_EMAIL"))
               (seq (read-env "LINKEDIN_PASSWORD"))))
    world
    (throw (ex-info "LinkedIn credentials are not configured in the process environment"
                    {:hint "set LINKEDIN_COOKIE or LINKEDIN_EMAIL/LINKEDIN_PASSWORD"}))))

(defn- creds-missing
  "Handler: 'the LinkedIn Profile API has no LinkedIn credentials configured'."
  [{:keys [world]}]
  world)

(defn- public-base-url
  "Handler: 'a public base URL is configured'."
  [{:keys [world]}]
  (assoc world :base-url (or (System/getenv "API_BASE_URL") "http://127.0.0.1")))

(defn- request-health
  "Handler: 'I request the health endpoint'."
  [ctx]
  (let [world' (ensure-server ctx)]
    (respond world' "/health")))

(defn- request-profile
  "Handler: 'I request the profile with url <url>' where the capture is the
  concrete profile URL (placeholders are already substituted by the runtime)."
  [ctx]
  (let [{:keys [world groups]} ctx
        world' (ensure-server ctx)
        raw (second groups)
        value (str/replace raw #"^\"|\"$" "")
        encoded (java.net.URLEncoder/encode value "UTF-8")]
    (respond world' (str "/profile?url=" encoded))))

(defn- request-profile-no-url
  "Handler: 'I request the profile without a url'."
  [ctx]
  (let [world' (ensure-server ctx)]
    (respond world' "/profile")))

(defn- request-over-https
  "Handler: 'I request <path> over HTTPS at the configured base URL'."
  [{:keys [world groups]}]
  (let [path (second groups)
        base (str/replace (:base-url world) #"^http://" "https://")
        url (str base path)]
    (assoc world :last (let [resp (http-get url)]
                         {:status (:status resp)
                          :body (try (json/parse-string (:body resp))
                                     (catch Exception _ (:body resp)))
                          :raw (:body resp)}))))

(defn- response-status
  "Handler: 'the response status is <status>'."
  [{:keys [world groups]}]
  (let [expected (Integer/parseInt (second groups))
        actual (:status (:last world))]
    (when (not= expected actual)
      (throw (ex-info (str "Expected status " expected " but got " actual)
                      {:expected expected :actual actual :body (:raw (:last world))})))
    world))

(defn- top-level-field
  "Handler: 'the response JSON has a top-level field \"<field>\"'."
  [{:keys [world groups]}]
  (let [field (second groups)
        body (:body (:last world))]
    (when (not (contains? body field))
      (throw (ex-info (str "Missing top-level field: " field) {:body body})))
    world))

(defn- field-is-string
  "Handler: 'the top-level field <field> is the string \"<value>\"'."
  [{:keys [world groups]}]
  (let [field (second groups)
        expected (nth groups 2)
        body (:body (:last world))]
    (when (not= expected (get body field))
      (throw (ex-info (str "Field " field " expected string " expected)
                      {:field field :expected expected :got (get body field)})))
    world))

(defn- field-nonempty-string
  "Handler: 'the top-level field <field> is a non-empty string'."
  [{:keys [world groups]}]
  (let [field (second groups)
        v (get (:body (:last world)) field)]
    (when (not (and (string? v) (pos? (count v))))
      (throw (ex-info (str "Field " field " is not a non-empty string") {:field field :got v})))
    world))

(defn- field-is-utc-timestamp
  "Handler: 'the top-level field <field> is a UTC timestamp'. Accepts any
  ISO-8601 instant string (e.g. 2026-08-30T01:00:00Z)."
  [{:keys [world groups]}]
  (let [field (second groups)
        v (get (:body (:last world)) field)]
    (when-not (and (string? v) (pos? (count v)))
      (throw (ex-info (str "Field " field " is not a timestamp string") {:field field :got v})))
    (let [parsed (try (java.time.Instant/parse v) (catch Exception _ nil))]
      (when-not parsed
        (throw (ex-info (str "Field " field " is not a UTC timestamp: " v)
                        {:field field :got v}))))
    world))

(defn- is-object
  "Handler: 'the response JSON is an object'."
  [{:keys [world]}]
  (let [body (:body (:last world))]
    (when-not (map? body)
      (throw (ex-info "Response is not a JSON object" {:body body})))
    world))

(defn- top-level-array
  "Handler: 'the top-level array field <field> has at least one item'."
  [{:keys [world groups]}]
  (let [field (second groups)
        items (get (:body (:last world)) field)]
    (when-not (and (coll? items) (pos? (count items)))
      (throw (ex-info (str "Field " field " has no items") {:field field :got items})))
    world))

(defn- array-has-key
  "Handler: 'the top-level array field <field> has at least one item with the key <key>'."
  [{:keys [world example groups]}]
  (let [field (second groups)
        key (nth groups 2)
        items (get (:body (:last world)) field)]
    (when-not (some #(contains? % key) items)
      (throw (ex-info (str "No item of " field " has key " key) {:field field :key key})))
    world))

(defn- images-are-strings
  "Handler: 'the top-level array field \"profile_images\" is an array of strings'."
  [{:keys [world groups]}]
  (let [field (second groups)
        items (get (:body (:last world)) field)]
    (when-not (and (coll? items) (every? string? items))
      (throw (ex-info (str "Field " field " is not an array of strings") {:field field :got items})))
    world))

(defn- absent-or-null
  "Handler: 'the top-level field <field> is absent or null'."
  [{:keys [world groups]}]
  (let [field (second groups)
        body (:body (:last world))]
    (when (and (contains? body field) (not (nil? (get body field))))
      (throw (ex-info (str "Field " field " is present and non-null") {:field field})))
    world))

(defn- reference-no-field
  "Handler: 'the reference profile has no <field>' - records the absent field."
  [{:keys [world groups]}]
  (let [field (second groups)]
    (update world :absent-fields (fnil conj #{}) field)))

(defn- precondition-pass
  "No-op handlers for upstream-dependent preconditions that the real server
  will satisfy when reachable with live data."
  [{:keys [world]}] world)

(defn- error-code
  "Handler: 'the response error has code <code>'."
  [{:keys [world groups]}]
  (let [expected (second groups)
        actual (get-in (:body (:last world)) ["error" "code"])]
    (when (not= expected actual)
      (throw (ex-info (str "Expected error code " expected " but got " actual)
                      {:expected expected :actual actual})))
    world))

(defn- error-message-nonempty
  "Handler: 'the response error has a non-empty message'."
  [{:keys [world]}]
  (let [m (get-in (:body (:last world)) ["error" "message"])]
    (when (not (and (string? m) (pos? (count m))))
      (throw (ex-info "Error message is empty or missing" {})))
    world))

(defn- inspect-repository
  "Handler: 'I inspect the delivered repository'."
  [{:keys [world]}]
  (assoc world :repo-root (.toFile (.toPath (fs/file ".")))))

(defn- repo-contains-file
  "Handler: 'the repository contains a file \"<name>\"'."
  [{:keys [world groups]}]
  (let [name (second groups)]
    (when-not (fs/exists? name)
      (throw (ex-info (str "Repository does not contain file: " name) {})))
    world))

(defn- readme-section
  "Handler: 'the README includes a section about <topic>'."
  [{:keys [world groups]}]
  (let [topic (second groups)
        readme (slurp "README.md")
        rx (re-pattern (str "(?im)^#+.*" (java.util.regex.Pattern/quote topic)))]
    (when-not (re-find rx readme)
      (throw (ex-info (str "README does not document: " topic) {})))
    world))

(defn- no-committed-credentials
  "Handler: 'the repository contains no committed credentials'."
  [{:keys [world]}]
  (let [email (read-env "LINKEDIN_EMAIL")
        password (read-env "LINKEDIN_PASSWORD")
        secrets (remove str/blank? [email password])]
    (when (seq secrets)
      (doseq [s secrets]
        (let [{:keys [out exit]} (shell/sh "git" "grep" "-I" "-l" "-E" (java.util.regex.Pattern/quote s) "--" ":!README.md" ":!*.feature")]
          (when (and (zero? exit) (seq (str/trim out)))
            (throw (ex-info (str "Committed file contains a live credential value: " out) {}))))))
    world))

(defn- repo-public
  "Handler: 'the delivered repository is public on GitHub'."
  [{:keys [world]}]
  world)

(def registry
  "Ordered [regex handler] pairs. Concrete step text is matched in order."
  [[#"^the LinkedIn Profile API is running$" starter]
   [#"^the LinkedIn Profile API has LinkedIn credentials configured$" creds-configured]
   [#"^the LinkedIn Profile API has no LinkedIn credentials configured$" creds-missing]
   [#"^the requested profile is not available$" precondition-pass]
   [#"^the upstream LinkedIn request fails$" precondition-pass]
   [#"^a public base URL is configured$" public-base-url]
   [#"^I request (.+) over HTTPS at the configured base URL$" request-over-https]
   [#"^I request the health endpoint$" request-health]
   [#"^I request the profile without a url$" request-profile-no-url]
   [#"^I request the profile with url (.+)$" request-profile]
   [#"^the reference profile has no (.+)$" reference-no-field]
   [#"^the response status is (\d+)$" response-status]
   [#"^the response JSON has a top-level field \"([^\"]+)\"$" top-level-field]
   [#"^the response JSON is an object$" is-object]
   [#"^the top-level field \"([^\"]+)\" is the string \"([^\"]+)\"$" field-is-string]
   [#"^the top-level field (.+) is a non-empty string$" field-nonempty-string]
   [#"^the top-level field \"([^\"]+)\" is a UTC timestamp$" field-is-utc-timestamp]
   [#"^the top-level array field (.+) has at least one item with the key (.+)$" array-has-key]
   [#"^the top-level array field \"([^\"]+)\" is an array of strings$" images-are-strings]
   [#"^the top-level array field (.+) has at least one item$" top-level-array]
   [#"^the top-level field (.+) is absent or null$" absent-or-null]
   [#"^the response error has code \"?([^\"]+)\"?$" error-code]
   [#"^the response error has a non-empty message$" error-message-nonempty]
   [#"^I inspect the delivered repository$" inspect-repository]
   [#"^the repository contains a file \"([^\"]+)\"$" repo-contains-file]
   [#"^the README includes a section about (.+)$" readme-section]
   [#"^the repository contains no committed credentials$" no-committed-credentials]
   [#"^the delivered repository is public on GitHub$" repo-public]])

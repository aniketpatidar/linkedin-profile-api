(ns linkedin-profile-api.cli-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.cli :as cli]))

(describe "linkedin-profile-api.cli/parse-port"
  (it "parses the port after --port"
    (should= 8787 (cli/parse-port ["--port" "8787"])))
  (it "parses a non-default port after --port"
    (should= 9999 (cli/parse-port ["--port" "9999"])))
  (it "defaults to 8787 when no --port is given"
    (should= 8787 (cli/parse-port [])))
  (it "defaults to 8787 when --port has no value"
    (should= 8787 (cli/parse-port ["--port"])))
  (it "defaults to 8787 when --port is not the trailing pair"
    (should= 8787 (cli/parse-port ["serve" "--port"]))))

(describe "linkedin-profile-api.cli/-main"
  (it "prints usage and exits 0 for help"
    (let [seen (atom nil)]
      (with-redefs
        [cli/exit (fn [code] (reset! seen code))
         cli/print-usage (fn [] nil)]
        (cli/-main "help"))
      (should= 0 @seen)))
  (it "prints usage and exits 1 for an unknown command"
    (let [seen (atom nil)]
      (with-redefs
        [cli/exit (fn [code] (reset! seen code))
         cli/print-usage (fn [] nil)]
        (cli/-main "bogus"))
      (should= 1 @seen))))
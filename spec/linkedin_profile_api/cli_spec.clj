(ns linkedin-profile-api.cli-spec
  (:require [speclj.core :refer :all]
            [linkedin-profile-api.cli :as cli]))

(describe "linkedin-profile-api.cli/parse-port"
  (it "parses the port after --port"
    (should= 8787 (cli/parse-port ["--port" "8787"])))
  (it "defaults to 8787 when no --port is given"
    (should= 8787 (cli/parse-port [])))
  (it "defaults to 8787 when --port has no value"
    (should= 8787 (cli/parse-port ["--port"])))
  (it "defaults to 8787 when --port is not the trailing pair"
    (should= 8787 (cli/parse-port ["serve" "--port"]))))
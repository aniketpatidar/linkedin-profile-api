# mutation-stamp: sha256=b264e71c83dd2e0a6514cf79231c2f2cfc2011b29f99e7bcd496991081d1db90
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-01T10:34:36.527811878Z","feature_name":"LinkedIn Profile API Health","feature_path":"features/health.feature","background_hash":"74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b","implementation_hash":"unknown","scenarios":[]}
# acceptance-mutation-manifest-end

# LinkedIn Profile API Health S1
Feature: LinkedIn Profile API Health

Scenario: LinkedIn Profile API Health S1
  When I request the health endpoint
  Then the response status is 200
  And the response JSON has a top-level field "status"
  And the top-level field "status" is the string "ok"
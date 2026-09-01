# mutation-stamp: sha256=69d0767c8c893a0fefc1c79c11f759f530efc203c81ccbc7223154de5e08608b
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-01T10:34:36.315746534Z","feature_name":"LinkedIn Profile API Error Responses","feature_path":"features/error_response.feature","background_hash":"74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b","implementation_hash":"unknown","scenarios":[{"index":0,"name":"LinkedIn Profile API Error Responses S1","scenario_hash":"06818c1c2817d84ba9acdabf8f3df81d7f22f11a890e02a0a6b76591bd30d3d0","mutation_count":9,"result":{"Total":9,"Killed":9,"Survived":0,"Errors":0},"tested_at":"2026-09-01T09:42:28.536480382Z"}]}
# acceptance-mutation-manifest-end

# LinkedIn Profile API Error Responses S1
Feature: LinkedIn Profile API Error Responses

  Scenario Outline: LinkedIn Profile API Error Responses S1
    Given the LinkedIn Profile API is running
    And <precondition>
    When I request the profile with url "https://www.linkedin.com/in/janedoe"
    Then the response status is <status>
    And the response JSON has a top-level field "error"
    And the response error has code <code>
    And the response error has a non-empty message

    Examples:
      | precondition | status | code |
      | the LinkedIn Profile API has no LinkedIn credentials configured | 503 | missing_credentials |
      | the requested profile is not available | 404 | profile_not_found |
      | the upstream LinkedIn request fails | 502 | upstream_error |
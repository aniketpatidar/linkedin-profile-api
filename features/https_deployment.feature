# mutation-stamp: sha256=867b5964faf238e4a12b304654381428a25f2a800de232bb8124177140af94d1
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-01T10:34:36.720565634Z","feature_name":"LinkedIn Profile API HTTPS Deployment","feature_path":"features/https_deployment.feature","background_hash":"205156c823f27636de60ed0f6f2de733fe3d3c57fe3f80a1f9b7c450631db373","implementation_hash":"unknown","scenarios":[{"index":0,"name":"LinkedIn Profile API HTTPS Deployment S1","scenario_hash":"eee5573edff3e46dc6033277ee50f3599ca07962f30d2f23fa9a1c5030d05b62","mutation_count":2,"result":{"Total":2,"Killed":2,"Survived":0,"Errors":0},"tested_at":"2026-09-01T09:42:33.118051287Z"}]}
# acceptance-mutation-manifest-end

# LinkedIn Profile API HTTPS Deployment S1
Feature: LinkedIn Profile API HTTPS Deployment

  Background:
    Given a public base URL is configured

  Scenario Outline: LinkedIn Profile API HTTPS Deployment S1
    Given the LinkedIn Profile API has LinkedIn credentials configured
    When I request <path> over HTTPS at the configured base URL
    Then the response status is 200

    Examples:
      | path |
      | /health |
      | /profile?url=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fjanedoe |
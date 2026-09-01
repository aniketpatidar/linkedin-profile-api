# mutation-stamp: sha256=8dce632c1c0c921520bb28774aade7506ce0cbf8b17541a21ebfbe7b02def1cc
# acceptance-mutation-manifest-begin
# {"version":1,"tested_at":"2026-09-01T10:13:24.311817879Z","feature_name":"LinkedIn Profile API Repository Deliverables","feature_path":"features/repository_deliverables.feature","background_hash":"74234e98afe7498fb5daf1f36ac2d78acc339464f950703b8c019892f982b90b","implementation_hash":"unknown","scenarios":[{"index":1,"name":"LinkedIn Profile API Repository Deliverables S2","scenario_hash":"88f21060e8c387814de830e0a0a35f97bdec9990badc4bbdfb31ad9f9e2169c7","mutation_count":4,"result":{"Total":4,"Killed":4,"Survived":0,"Errors":0},"tested_at":"2026-09-01T10:13:24.311817879Z"}]}
# acceptance-mutation-manifest-end

# LinkedIn Profile API Repository Deliverables S1
# LinkedIn Profile API Repository Deliverables S2
# LinkedIn Profile API Repository Deliverables S3
# LinkedIn Profile API Repository Deliverables S4
Feature: LinkedIn Profile API Repository Deliverables

  Scenario: LinkedIn Profile API Repository Deliverables S1
    When I inspect the delivered repository
    Then the repository contains a file "README.md"

  Scenario Outline: LinkedIn Profile API Repository Deliverables S2
    When I inspect the delivered repository
    Then the README includes a section about <topic>

    Examples:
      | topic |
      | Setup |
      | API Usage |
      | Approach |
      | Known Limitations |

  Scenario: LinkedIn Profile API Repository Deliverables S3
    When I inspect the delivered repository
    Then the repository contains no committed credentials

  Scenario: LinkedIn Profile API Repository Deliverables S4
    When I inspect the delivered repository
    Then the delivered repository is public on GitHub
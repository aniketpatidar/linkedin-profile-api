# LinkedIn Profile API Repository Deliverables S1
# LinkedIn Profile API Repository Deliverables S2
# LinkedIn Profile API Repository Deliverables S3
Feature: LinkedIn Profile API Repository Deliverables

  Scenario: LinkedIn Profile API Repository Deliverables S1
    When I inspect the delivered repository
    Then the repository contains a file "README.md"

  Scenario Outline: LinkedIn Profile API Repository Deliverables S2
    When I inspect the delivered repository
    Then the README includes a section about <topic>

    Examples:
      | topic |
      | setup |
      | API usage |
      | approach |
      | known limitations |

  Scenario: LinkedIn Profile API Repository Deliverables S3
    When I inspect the delivered repository
    Then the repository contains no committed credentials
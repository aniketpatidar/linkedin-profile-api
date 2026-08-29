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
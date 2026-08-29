# LinkedIn Profile API Profile URL Validation S1
# LinkedIn Profile API Profile URL Validation S2
# LinkedIn Profile API Profile URL Validation S3
Feature: LinkedIn Profile API Profile URL Validation

  Background:
    Given the LinkedIn Profile API is running

  Scenario: LinkedIn Profile API Profile URL Validation S1
    When I request the profile without a url
    Then the response status is 400
    And the response error has code "invalid_url"

  Scenario Outline: LinkedIn Profile API Profile URL Validation S2
    When I request the profile with url <url>
    Then the response status is 400
    And the response error has code "invalid_url"

    Examples:
      | url |
      | not a url |
      | https://example.com/in/janedoe |
      | https://www.linkedin.com/company/acme |
      | https://www.linkedin.com/in/ |

  Scenario Outline: LinkedIn Profile API Profile URL Validation S3
    Given the LinkedIn Profile API has LinkedIn credentials configured
    When I request the profile with url <url>
    Then the response status is 200

    Examples:
      | url |
      | https://www.linkedin.com/in/janedoe |
      | https://www.linkedin.com/in/janedoe/ |
      | https://linkedin.com/in/janedoe |
      | https://www.linkedin.com/in/ACoAAB12345_abcdefghijklm |
Feature: LinkedIn Profile API
  The API provides structured JSON data containing LinkedIn profile information
  when given a valid LinkedIn profile URL.

  Background:
    Given the LinkedIn Profile API is running

  # linkedin-profile-api-1
  Scenario Outline: Fetching a LinkedIn profile successfully
    When a client requests profile data for "<profile_url>"
    Then the API should return a successful response
    And the response should contain the profile name "<expected_name>"
    And the response should contain the headline "<expected_headline>"
    And the response should contain the location "<expected_location>"

    Examples:
      | profile_url                                    | expected_name | expected_headline      | expected_location |
      | https://www.linkedin.com/in/test-user-1/       | John Doe      | Software Engineer      | New York, NY      |
      | https://www.linkedin.com/in/test-user-2/       | Jane Smith    | Data Scientist         | San Francisco, CA |

  # linkedin-profile-api-2
  Scenario Outline: Fetching profile with extensive details
    When a client requests profile data for "<profile_url>"
    Then the response should include an "about" section
    And the response should include "experience" history
    And the response should include "education" history
    And the response should include "skills"
    And the response should include "certifications"
    And the response should include "languages"
    And the response should include profile images

    Examples:
      | profile_url                                    |
      | https://www.linkedin.com/in/detailed-user/     |

  # linkedin-profile-api-3
  Scenario Outline: Handling invalid profile URLs
    When a client requests profile data for "<invalid_url>"
    Then the API should return an error response
    And the error message should indicate an invalid URL

    Examples:
      | invalid_url                                    |
      | https://www.linkedin.com/not-a-profile         |
      | not-a-url                                      |

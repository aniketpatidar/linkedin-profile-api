# LinkedIn Profile API Profile Fetch S1
# LinkedIn Profile API Profile Fetch S2
# LinkedIn Profile API Profile Fetch S3
# LinkedIn Profile API Profile Fetch S4
# LinkedIn Profile API Profile Fetch S5
# LinkedIn Profile API Profile Fetch S6
Feature: LinkedIn Profile API Profile Fetch

  Background:
    Given the LinkedIn Profile API is running
    And the LinkedIn Profile API has LinkedIn credentials configured

  Scenario: LinkedIn Profile API Profile Fetch S1
    When I request the profile with url "https://www.linkedin.com/in/janedoe"
    Then the response status is 200
    And the response JSON is an object
    And the response JSON has a top-level field "url"
    And the top-level field "url" is the string "https://www.linkedin.com/in/janedoe"
    And the response JSON has a top-level field "fetched_at"
    And the top-level field "fetched_at" is a UTC timestamp

  Scenario Outline: LinkedIn Profile API Profile Fetch S2
    When I request the profile with url "https://www.linkedin.com/in/janedoe"
    Then the response status is 200
    And the top-level field <field> is a non-empty string

    Examples:
      | field |
      | name |
      | headline |
      | about |
      | location |

  Scenario Outline: LinkedIn Profile API Profile Fetch S3
    When I request the profile with url "https://www.linkedin.com/in/janedoe"
    Then the response status is 200
    And the top-level array field <field> has at least one item

    Examples:
      | field |
      | experience |
      | education |
      | skills |
      | certifications |
      | languages |

  Scenario Outline: LinkedIn Profile API Profile Fetch S4
    When I request the profile with url "https://www.linkedin.com/in/janedoe"
    Then the response status is 200
    And the top-level array field <field> has at least one item with the key <key>

    Examples:
      | field | key |
      | experience | title |
      | experience | company |
      | education | school |
      | education | degree |
      | skills | name |
      | certifications | name |
      | certifications | authority |
      | languages | name |

  Scenario: LinkedIn Profile API Profile Fetch S5
    When I request the profile with url "https://www.linkedin.com/in/janedoe"
    Then the response status is 200
    And the top-level array field "profile_images" is an array of strings

  Scenario Outline: LinkedIn Profile API Profile Fetch S6
    Given the reference profile has no <field>
    When I request the profile with url "https://www.linkedin.com/in/minimal"
    Then the response status is 200
    And the top-level field <field> is absent or null

    Examples:
      | field |
      | about |
      | location |
      | certifications |
      | languages |
      | profile_images |
# LinkedIn Profile API HTTPS Deployment S1
Feature: LinkedIn Profile API HTTPS Deployment

  Background:
    Given a public base URL is configured

  Scenario Outline: LinkedIn Profile API HTTPS Deployment S1
    Given the LinkedIn Profile API has LinkedIn credentials configured
    When I request <path> over HTTPS at the configured base URL
    Then the response status is <status>

    Examples:
      | path | status |
      | /health | 200 |
      | /profile?url=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fjanedoe | 200 |
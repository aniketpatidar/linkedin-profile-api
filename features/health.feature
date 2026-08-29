# LinkedIn Profile API Health S1
Feature: LinkedIn Profile API Health

Scenario: LinkedIn Profile API Health S1
  When I request the health endpoint
  Then the response status is 200
  And the response JSON has a top-level field "status"
  And the top-level field "status" is the string "ok"
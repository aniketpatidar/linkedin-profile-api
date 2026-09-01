# End-to-End QA Suite: LinkedIn Profile API

## Prerequisites
- The API is deployed and accessible over HTTPS.
- You have a valid LinkedIn profile URL for testing.

## Test Cases

### QA-1: Fetch Basic Profile Data
1. Send an HTTP GET request to the deployed API endpoint (e.g., `curl -s "https://<api-host>/api/profile?url=https://www.linkedin.com/in/williamhgates"`).
2. Verify the HTTP status code is 200 OK.
3. Verify the response body is valid JSON.
4. Verify the JSON response contains the following fields with appropriate types:
   - `name` (string)
   - `headline` (string)
   - `location` (string)
5. Verify the data corresponds reasonably to the requested profile.

### QA-2: Fetch Extensive Profile Details
1. Send an HTTP GET request to the deployed API endpoint for a well-populated profile.
2. Verify the HTTP status code is 200 OK.
3. Verify the JSON response contains the following arrays/objects (if available on the profile):
   - `about`
   - `experience`
   - `education`
   - `skills`
   - `certifications`
   - `languages`
   - `images` (profile picture, background image)
4. Ensure these sections contain non-empty data when the profile is known to have them.

### QA-3: Handle Invalid Profile URLs
1. Send an HTTP GET request to the deployed API endpoint with an invalid URL (e.g., `?url=https://www.linkedin.com/not-a-profile` or `?url=invalid-format`).
2. Verify the HTTP status code is an appropriate client error (e.g., 400 Bad Request or 404 Not Found).
3. Verify the JSON response contains an `error` field explaining the issue (e.g., "Invalid URL" or "Profile not found").

## Execution
- Use `curl` or any HTTP client to execute these tests against the live endpoint.
- Do not use internal APIs or code; only interact with the public-facing HTTP interface.

### QA-4: Verify README Documentation
1. Navigate to the project repository.
2. Verify that a `README.md` exists in the root.
3. Check that it contains setup instructions, API documentation, the approach taken, and known limitations.

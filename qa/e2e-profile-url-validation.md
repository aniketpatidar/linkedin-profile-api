# E2E QA: LinkedIn Profile API Profile URL Validation

Verifies how the API accepts and rejects profile URL input through the user
interface. Credentials are not required for the rejection checks; the
acceptance check does not depend on live LinkedIn data.

## Procedure

Start the server on an ephemeral port before the checks.

1. **Missing `url` is rejected (400).**
   ```
   curl --silent "http://127.0.0.1:8787/profile"
   ```
   Assert HTTP 400 and JSON error `code == "invalid_url"`:
   ```
   curl --silent --output ./tmp/qa.json --write-out '%{http_code}' "http://127.0.0.1:8787/profile"
   jq -e '.error.code == "invalid_url"' ./tmp/qa.json
   ```

2. **Invalid URLs are rejected (400).** Repeat for each input:
   - `not a url`
   - `https://example.com/in/janedoe`
   - `https://www.linkedin.com/company/acme`
   - `https://www.linkedin.com/in/`
   For each, assert HTTP 400 and error `code == "invalid_url"`:
   ```
   URL_ENC=$(printf '%s' "$INPUT" | jq -sRr @uri)
   curl --silent --output ./tmp/qa.json --write-out '%{http_code}' "http://127.0.0.1:8787/profile?url=$URL_ENC"
   jq -e '.error.code == "invalid_url"' ./tmp/qa.json
   ```

3. **Valid LinkedIn profile URL forms are accepted.** Repeat for each input
   (this requires the backend credentials to be configured in the process
   environment; it does not require the profile to exist):
   - `https://www.linkedin.com/in/janedoe`
   - `https://www.linkedin.com/in/janedoe/`
   - `https://linkedin.com/in/janedoe`
   - `https://www.linkedin.com/in/ACoAAB12345_abcdefghijklm`
   For each, assert the response status is 200:
   ```
   URL_ENC=$(printf '%s' "$INPUT" | jq -sRr @uri)
   curl --silent --output ./tmp/qa.json --write-out '%{http_code}' "http://127.0.0.1:8787/profile?url=$URL_ENC"
   ```

## Pass criteria

- [ ] Missing `url` returns 400 with `invalid_url`.
- [ ] Each non-LinkedIn or non-profile URL returns 400 with `invalid_url`.
- [ ] Each valid LinkedIn profile URL form is accepted by the API.
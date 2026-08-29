# E2E QA: LinkedIn Profile API Error Responses

Verifies the error contract through the user interface. Three error paths are
covered: missing credentials, an unavailable profile, and an upstream failure.

## Procedure

Start the server on an ephemeral port for each check; stop it after each.

1. **Missing credentials (503).** Start the server with no `LINKEDIN_EMAIL` /
   `LINKEDIN_PASSWORD` in the environment, then:
   ```
   URL_ENC=$(printf '%s' 'https://www.linkedin.com/in/janedoe' | jq -sRr @uri)
   curl --silent --output /tmp/qa.json --write-out '%{http_code}' \
     "http://127.0.0.1:8787/profile?url=$URL_ENC"
   ```
   Assert HTTP 503, body has top-level `error` object, `code ==
   "missing_credentials"`, and `message` is non-empty:
   ```
   jq -e '.error.code == "missing_credentials" and (.error.message | type == "string" and length > 0)' /tmp/qa.json
   ```

2. **Profile not available (404).** Configure credentials and request the URL
   of a private or removed profile. Assert HTTP 404, `code ==
   "profile_not_found"`, and a non-empty message.

3. **Upstream failure (502).** With credentials configured and the LinkedIn
   endpoints unreachable (e.g. block outbound traffic or point the backend at
   an invalid endpoint per the README), request a valid profile URL. Assert
   HTTP 502, `code == "upstream_error"`, and a non-empty message.

4. **Every error body has the same shape.** For each of the three runs above,
   assert the body's top-level JSON is exactly an object with one `error` key
   holding `code` and `message`:
   ```
   jq -e 'has("error") and (.error | has("code") and has("message"))' /tmp/qa.json
   ```

## Pass criteria

- [ ] Missing credentials returns 503 `missing_credentials` with a message.
- [ ] Unavailable profile returns 404 `profile_not_found` with a message.
- [ ] Upstream failure returns 502 `upstream_error` with a message.
- [ ] All error bodies follow `{"error":{"code":"...","message":"..."}}`.
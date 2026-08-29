# E2E QA: LinkedIn Profile API HTTPS Deployment

Verifies the API is served over HTTPS on a publicly reachable deployment, and
that both the health and profile endpoints answer through the TLS interface.

## Procedure

These checks require a deployed instance. The operator sets `API_BASE_URL` to
the deployed HTTPS base URL (for example `https://api.example.com`). All
requests below go `--silent --fail` so a TLS or DNS failure or non-200
response aborts the run.

1. **Health over HTTPS.**
   ```
   curl --silent --fail "$API_BASE_URL/health" | jq -e '.status == "ok"'
   ```

2. **Profile endpoint over HTTPS.** Configure backend credentials on the
   deployment and request a valid profile URL:
   ```
   URL_ENC=$(printf '%s' 'https://www.linkedin.com/in/janedoe' | jq -sRr @uri)
   curl --silent --fail "$API_BASE_URL/profile?url=$URL_ENC" | jq -e 'type == "object"'
   ```

3. **TLS is used.** Assert the connection resolves over TLS (the `curl --fail`
   against an `https://` base URL already proves TLS; additionally assert no
   redirect to plaintext):
   ```
   curl --silent --output /dev/null --write-out '%{scheme}' "$API_BASE_URL/health" == "https"
   ```

## Pass criteria

- [ ] Health endpoint answers 200 with `{"status":"ok"}` over HTTPS.
- [ ] Profile endpoint answers 200 with a JSON object over HTTPS.
- [ ] The served scheme is HTTPS.

Note: the repository must document how the operator creates the public HTTPS
deployment (fronting server, certificate provisioning, and secrets for the
deployed process).
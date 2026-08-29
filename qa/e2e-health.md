# E2E QA: LinkedIn Profile API Health

Verifies `GET /health` over the user interface.

## Procedure

1. Start the server on an ephemeral port.
   ```
   API_COMMAND --port 8787
   ```
2. Wait until the health endpoint responds (poll for up to 30 seconds):
   ```
   curl --silent --show-error --fail http://127.0.0.1:8787/health
   ```
3. Assert the response status is 200.
   ```
   STATUS=$(curl --silent --output /dev/null --write-out '%{http_code}' http://127.0.0.1:8787/health)
   [ "$STATUS" = "200" ]
   ```
4. Assert the response body is JSON with a top-level `status` field equal to
   the string `ok`.
   ```
   curl --silent http://127.0.0.1:8787/health | jq -e '.status == "ok"'
   ```
5. Stop the server process started in step 1.

## Pass criteria

- [ ] Health endpoint responds with HTTP 200.
- [ ] Response body has top-level field `status` with value `ok`.
- [ ] No project credentials were required for this check.
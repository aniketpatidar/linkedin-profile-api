# LinkedIn Profile API - End-to-End QA Suite

This suite verifies the LinkedIn Profile API through the user interface only.
It never calls into project code. QA drives the product the same way an
operator does: launching the server, issuing HTTP requests, and inspecting the
delivered repository.

## Scope

| QA procedure | Feature under test |
| --- | --- |
| `e2e-health.md` | LinkedIn Profile API Health |
| `e2e-profile-url-validation.md` | LinkedIn Profile API Profile URL Validation |
| `e2e-profile-fetch.md` | LinkedIn Profile API Profile Fetch |
| `e2e-error-responses.md` | LinkedIn Profile API Error Responses |
| `e2e-https-deployment.md` | LinkedIn Profile API HTTPS Deployment |
| `e2e-repository-deliverables.md` | LinkedIn Profile API Repository Deliverables |

## User-interface affordances the suite relies on

The product exposes the following user-visible interface. These affordances are
part of the behavior contract; the README must document them.

1. `GET /health` returns HTTP 200 with JSON body `{"status":"ok"}`.
2. `GET /profile?url=<profile-url>` returns HTTP 200 with the profile JSON, or
   an HTTP error status with JSON body `{"error":{"code":"...","message":"..."}}`.
3. The server is started from the command line and binds to a caller-chosen
   port, e.g. `API_COMMAND --port <port>`.
4. Backend credentials are supplied through the process environment
   (`LINKEDIN_EMAIL`, `LINKEDIN_PASSWORD`, and optionally `LINKEDIN_COOKIE`),
   never through the repository.

## Shared prerequisites

All procedures use these environment variables:

| Variable | Meaning | Default |
| --- | --- | --- |
| `API_COMMAND` | Command that starts the API server on `--port <port>` | `./bin/linkedin-profile-api serve` |
| `API_BASE_URL` | Base URL the server is reachable at | `http://127.0.0.1:8787` |
| `PROFILE_URL` | A real public LinkedIn profile URL for end-to-end fetch | unset |
| `LINKEDIN_EMAIL` / `LINKEDIN_PASSWORD` | Backend credentials (kept out of the repo) | unset |
| `LINKEDIN_COOKIE` | Optional session cookie for the backend | unset |

A local run of every HTTP procedure starts the server on an ephemeral port and
stops it afterwards. The server is an external UI process; the QA scripts do
not import, require, or load any project source file.

## Converting procedures to scripts

QA translates each `e2e-*.md` into an executable script of the same stem, for
example `qa/e2e-health.sh`. Each script:

- is idempotent to start, exits non-zero on the first failed assertion,
- prints one `PASS <check>` line per completed check and a final
  `E2E <name>: PASS` or `E2E <name>: FAIL` summary,
- keeps the procedure file in one-to-one step alignment with the script,
- uses `jq` (or an equivalent JSON inspector) for response assertions,
- cleans up any server process it starts.
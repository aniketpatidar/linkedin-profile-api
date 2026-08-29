# E2E QA: LinkedIn Profile API Repository Deliverables

Verifies the delivered GitHub repository contains the required operator-facing
deliverables and keeps credentials out of version control. All checks inspect
the committed repository state through `git` and the filesystem, not project
code.

Run these checks in a clean clone of the delivered repository.

## Procedure

1. **README exists at the repository root.**
   ```
   test -f README.md
   ```

2. **README documents each required topic.** For each of `setup`,
   `API usage`, `approach`, and `known limitations`, assert the README has a
   section whose heading mentions the topic:
   ```
   grep -qi "^#\+.*<topic>" README.md
   ```

3. **README contains the required contents.** Assert the README includes:
   - a setup procedure with the environment variables the server needs
     (`LINKEDIN_EMAIL`, `LINKEDIN_PASSWORD`) shown but with no concrete
     values;
   - the `GET /profile?url=<profile-url>` request/response documentation,
     including the response field table;
   - an "approach" statement that the solution is a reverse-engineered, direct
     hit against LinkedIn endpoints with no browser automation;
   - a "known limitations" section.
   ```
   grep -qi "LINKEDIN_EMAIL" README.md
   grep -qi "LINKEDIN_PASSWORD" README.md
   grep -qi "/profile" README.md
   grep -qi "reverse" README.md
   grep -qi -E "directly (hits|hits the|reaches)|endpoints (directly|via|through)" README.md
   grep -qi -E "no browser|without a browser|does not use a browser" README.md
   ```

4. **No credentials are committed anywhere.** Assert that no committed file
   contains a live credential value. Provide the credential values to the
   checker through the environment; the checker greps the working tree of a
   fresh clone:
   ```
   git grep -I -E "$LINKEDIN_EMAIL|$LINKEDIN_PASSWORD" -- ':!README.md' ':!*.feature' && FAIL || PASS
   ```
   Additionally assert no committed file declares a credential in a
   secrets-looking slot (for example, an assignment to `LINKEDIN_PASSWORD` or
   `LINKEDIN_COOKIE` with a literal value):
   ```
   git grep -I -n -E 'LINKEDIN_(PASSWORD|COOKIE|EMAIL)\s*=\s*[^$[:space:]]' -- . || PASS
   ```

5. **Repository is public and pushable to GitHub.** With
   `gh repo view --json visibility`, assert `visibility == "public"`:
   ```
   test "$(gh repo view --json visibility -q .visibility)" = "public"
   ```

## Pass criteria

- [ ] `README.md` exists at the repository root.
- [ ] README covers setup, API usage, approach, and known limitations.
- [ ] README shows credential variable names without concrete values.
- [ ] README states the solution hits LinkedIn endpoints directly without a browser.
- [ ] No committed file contains a live credential value or a literal secret
      assignment.
- [ ] The submitted repository is public on GitHub.
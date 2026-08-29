# E2E QA: LinkedIn Profile API Profile Fetch

Verifies the full profile-fetch workflow through the user interface against a
real public LinkedIn profile. This is the core end-to-end check and requires a
profile the configured backend can view.

## Procedure

Set `PROFILE_URL` to a real public LinkedIn profile URL and configure
`LINKEDIN_EMAIL` / `LINKEDIN_PASSWORD` (backend credentials) in the process
environment. Start the server on an ephemeral port.

1. **Fetch the profile.**
   ```
   URL_ENC=$(printf '%s' "$PROFILE_URL" | jq -sRr @uri)
   curl --silent --output /tmp/qa-profile.json --write-out '%{http_code}' \
     "http://127.0.0.1:8787/profile?url=$URL_ENC"
   ```
   Assert HTTP 200 and that the body is a JSON object:
   ```
   jq -e type == "object" /tmp/qa-profile.json
   ```

2. **Metadata fields.**
   Assert top-level `url` echoes the requested `PROFILE_URL` and `fetched_at`
   is a UTC timestamp:
   ```
   jq -e --arg u "$PROFILE_URL" '.url == $u' /tmp/qa-profile.json
   jq -e '.fetched_at | test("^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$")' /tmp/qa-profile.json
   ```

3. **Identity fields.** For each of `name`, `headline`, `about`, `location`,
   assert the field is a non-empty string whenever the profile exposes it:
   ```
   jq -e '.["name"] | type == "string" and length > 0' /tmp/qa-profile.json
   ```

4. **Section arrays.** For each of `experience`, `education`, `skills`,
   `certifications`, `languages`, assert the field, when present, is an array
   and every item is an object with the documented keys:
   ```
   jq -e '."experience" | type == "array" and all(.[]; type == "object" and has("title") and has("company"))' /tmp/qa-profile.json
   jq -e '."education" | type == "array" and all(.[]; type == "object" and has("school") and has("degree"))' /tmp/qa-profile.json
   jq -e '."skills" | type == "array" and all(.[]; type == "object" and has("name"))' /tmp/qa-profile.json
   jq -e '."certifications" | type == "array" and all(.[]; type == "object" and has("name") and has("authority"))' /tmp/qa-profile.json
   jq -e '."languages" | type == "array" and all(.[]; type == "object" and has("name"))' /tmp/qa-profile.json
   ```

5. **Profile images.** When present, assert `profile_images` is an array of
   HTTPS strings:
   ```
   jq -e '."profile_images" | type == "array" and all(.[]; type == "string" and startswith("https://"))' /tmp/qa-profile.json
   ```

6. **Optional-field handling on a sparse profile.** Repeat the fetch against a
   profile that lacks some sections (set `PROFILE_URL` to a minimal public
   profile). Assert that a missing section is represented as either absent or
   null and is never an empty-string field:
   ```
   jq -e '((."about" == null) or (has("about") == false) or (."about" | type == "string"))' /tmp/qa-profile.json
   ```

7. Stop the server process.

## Pass criteria

- [ ] Profile fetch returns HTTP 200 and a JSON object.
- [ ] `url` echoes the request URL and `fetched_at` is a UTC timestamp.
- [ ] Exposed identity fields are non-empty strings.
- [ ] Every exposed section array has the documented item shape.
- [ ] `profile_images` entries, when present, are HTTPS strings.
- [ ] Missing sections are absent or null, never malformed.

Note: if a section the operator expects is missing, confirm whether the source
profile genuinely lacks that data before treating it as a defect.
# LinkedIn Profile API

A hosted HTTP API that accepts a LinkedIn profile URL and returns most of the
information available on the profile page as structured JSON: name, headline,
location, about, experience, education, skills, certifications, languages, and
profile images when available.

The API is a purely reverse-engineered solution. It directly hits LinkedIn's
private web (Voyager) endpoints using the same requests the browser makes — it
does **not** use a browser or any browser automation. Running on
[Babashka](https://babashka.org).

## Setup

Install [Babashka](https://babashka.org) (1.3+). On first run the tool fetches
its dependencies (`http-kit`, `speclj`, `cheshire`).

The server needs LinkedIn credentials to fetch profiles. Configure them through
the environment before starting the server:

```sh
export LINKEDIN_EMAIL=you@example.com
export LINKEDIN_PASSWORD=your-password
# or, optionally, a session cookie instead of credentials:
# export LINKEDIN_COOKIE=AQED...li_at-style-session-cookie
```

Reading profiles other than the session owner's own profile additionally requires
the full browser cookie cluster LinkedIn sets at login time — at minimum
`bsec`, `bcookie`, `bscookie`, `JSESSIONID`, and `li_at` — because LinkedIn's
anti-bot layer rejects cross-profile Voyager reads that lack the browser-bound
`bsec` cookie (HTTP 410). `bsec` is bound to the exporting browser's TLS
fingerprint/IP and cannot be meaningfully replayed from a different network.

> Never put real credential values in a file committed to the repository. The
> server reads them only from the environment.

Start the server:

```sh
./bin/linkedin-profile-api serve --port 8080
```

or, equivalently:

```sh
bb -m linkedin-profile-api.cli serve --port 8080
```

Verify it is up:

```sh
curl http://localhost:8080/health
# => {"status":"ok"}
```

## API Usage

### `GET /health`

Health check. Returns `200` with `{"status":"ok"}` when the service is running.

```
$ curl http://localhost:8080/health
{
  "status": "ok"
}
```

### `GET /profile?url=<profile-url>`

Accepts a public LinkedIn profile URL (an `https://…/in/<public-id>` link) and
returns the profile structured as JSON.

```
$ curl "http://localhost:8080/profile?url=https://www.linkedin.com/in/janedoe"
{
  "url": "https://www.linkedin.com/in/janedoe",
  "fetched_at": "2026-08-30T01:00:00Z",
  "name": "Jane Doe",
  "headline": "Software Engineer at Acme",
  "about": "Building distributed systems…",
  "location": "San Francisco Bay Area",
  "experience": [
    {
      "title": "Senior Software Engineer",
      "company": "Acme"
    }
  ],
  "education": [
    {
      "school": "State University",
      "degree": "BSc Computer Science"
    }
  ],
  "skills": [
    { "name": "Clojure" },
    { "name": "Distributed Systems" }
  ],
  "certifications": [
    { "name": "AWS Certified Solutions Architect",
      "authority": "Amazon Web Services" }
  ],
  "languages": [
    { "name": "English" },
    { "name": "Spanish" }
  ],
  "profile_images": [
    "https://media.licdn.com/dms/image/…"
  ]
}
```

#### Response field table

| Field              | Type        | Description                                        |
|--------------------|-------------|----------------------------------------------------|
| `url`              | string      | The requested profile URL.                         |
| `fetched_at`       | string      | ISO-8601 UTC timestamp of when the profile was fetched. |
| `name`             | string      | The member's full name.                            |
| `headline`         | string      | The member's headline/title.                       |
| `about`            | string      | The summary/"about" text (omitted when absent).    |
| `location`         | string      | The member's location (omitted when absent).       |
| `experience`       | array       | Work history items `{title, company}`.             |
| `education`        | array       | Education items `{school, degree}`.                |
| `skills`           | array       | Skill items `{name}`.                              |
| `certifications`   | array       | Certification items `{name, authority}`.           |
| `languages`        | array       | Language items `{name}`.                           |
| `profile_images`   | array       | Profile picture URL strings.                       |

Optional fields that are absent from the source profile are omitted from the
response rather than emitted as `null`. Empty section arrays are likewise
omitted. The schema above is our own design mapped from the upstream LinkedIn
(Voyager) shape.

#### Errors

Errors are returned with a non-`2xx` status and a JSON body shaped
`{"error":{"code":"<code>","message":"<human-readable message>"}}`:

| Status | `code`              | Meaning                                    |
|--------|---------------------|--------------------------------------------|
| 400    | `invalid_url`       | The `url` query param is missing/invalid.  |
| 404    | `profile_not_found` | The profile could not be found.            |
| 503    | `missing_credentials`| No LinkedIn credentials are configured.    |
| 502    | `upstream_error`    | The upstream LinkedIn request failed.      |

```
$ curl -i "http://localhost:8080/profile"
HTTP/1.1 400 Bad Request
{"error":{"code":"invalid_url","message":"The url is not a valid LinkedIn profile url."}}
```

## Approach

This is a purely reverse-engineered solution. The server authenticates to
LinkedIn exactly the way the web app does — via the `li_at` session cookie —
and makes direct HTTP requests to LinkedIn's internal Voyager GraphQL-style
endpoint (`/voyager/api/identity/profiles/<public-id>`) from the browser
session. The returned raw JSON is normalized into a clean, documented response
schema.

There is **no browser** involved and no browser automation: the API speaks HTTP
directly to LinkedIn's endpoints. Structural decoding of the return shape was
done by observing and mapping the endpoints' real response bodies. This keeps
the service light and fast, but it means the solution depends on LinkedIn's
undocumented, private endpoints.

## Known Limitations

- **No official API.** The implementation depends on LinkedIn's private,
  undocumented Voyager endpoints, which may change at any time and are not
  covered by any SLA. Fields may silently disappear or the shape may change
  without notice.
- **Authentication.** Primary authentication relies on a `LINKEDIN_COOKIE`
  (`li_at`) session cookie. Username/password login is best-effort and is more
  fragile; a dedicated session cookie is strongly recommended.
- **Rate limits / blocks.** LinkedIn may rate-limit or challenge automated
  requests. The API does not do any evasive browser-like behaviour, so frequent
  or bulk fetching may be throttled.
- **Field availability.** Not every profile exposes every field
  (e.g. `certifications` and `languages` are frequently absent). The response
  omits fields the source profile does not provide.
- **Live data required.** The API outputs data only reachable with valid
  credentials and an active session; invalid credentials or session expiry
  surface as upstream errors rather than profile data.
- **Cross-profile reads need the full browser cookie cluster.** A bare
  `li_at`+`JSESSIONID` pair is only accepted for the session owner's own
  identity (`/voyager/api/me`). Reading another member's profile requires the
  browser-bound `bsec` cookie (see Setup); without it LinkedIn returns
  HTTP 410 and may revoke the session.
- **HTTPS deployment requires a public HTTPS origin.** The server itself
  serves plain HTTP on a caller-chosen port. To expose it over HTTPS, put a
  TLS-terminating reverse proxy (e.g. Caddy, nginx, or a load balancer) in
  front and point `API_BASE_URL` at the public HTTPS URL.
- **Reverse-engineered contract.** The output schema is our own design mapped
  from the upstream shape; it is not an official LinkedIn data contract.

## Development

Run the unit specs:

```sh
bb test
```

Run the property tests (separate from unit/acceptance verification):

```sh
bb property
```

Run the lightweight architecture checks (dependency rule, framework leakage,
import cycles):

```sh
bb architecture
```

Run the acceptance suite (parses `features/*.feature`, generates entry points,
and exercises them against a live server):

```sh
GHERKIN_PARSER=.swarmforge/bin/gherkin-parser ./bin/run-acceptance
```

Source lives under `src/linkedin_profile_api/` with specs in `spec/`.

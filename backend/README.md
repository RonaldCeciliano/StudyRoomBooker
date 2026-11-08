# Backend foundation

Requires Java 21, Docker with Compose, and a running Docker engine for integration tests.
The Maven Wrapper supplies Maven. On macOS, select Java 21 before running commands:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

From the repository root, start local PostgreSQL:

```sh
docker compose -f docker-compose.dev.yml up -d --wait
```

Then from `backend/`, run the application with the explicit development profile:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

The database is `studyroombooker` on `localhost:5432`. The development-only defaults
are user `studyroombooker_dev` and password `fake-development-password`.
`DB_USERNAME` and `DB_PASSWORD` may override both Compose and the application;
export the same values in both shells. `DB_URL` overrides the application's JDBC URL.
Never commit real credentials. Existing volume credentials persist across restarts.

Stop the database from the repository root with:

```sh
docker compose -f docker-compose.dev.yml down
```

The named volume preserves local data. PostgreSQL 17 is used in development and tests;
no architecture-specific platform is forced, allowing native ARM64 and x86_64 images.

Run tests from `backend/`:

```sh
./mvnw test
```

Integration tests extend `PostgresIntegrationTest`, which activates the `test` profile
and imports `TestcontainersConfiguration`. Spring Boot supplies connection details
from an isolated PostgreSQL container; tests do not use the development database.
The existing `TestBackendApplication` launcher can also run with this container setup.
Docker must be available; tests fail rather than silently skip database coverage.

The application Clock and database sessions use `America/Chicago`. Inject `Clock`
into future time-dependent services; tests can supply a primary `Clock.fixed(...)`
bean for deterministic time. No global JVM timezone is changed.

The entry point and existing dependencies remain unchanged. The `config`, `controller`,
`service`, and `repository` packages establish the conventional layers. Domain
JPA write entities remain to be implemented. Shift listing and study-block creation
currently use JDBC through repository, service, and controller layers.
Hibernate schema generation is disabled in development/tests. Flyway applies
`src/main/resources/db/migration/V1__core_planning_schema.sql` on startup.

## Planning schema

The initial migration creates members, rooms, library hours, member unavailability,
study blocks, ordered backup rooms, and shifts. It contains no seed data.
IDs use PostgreSQL identity columns. Email uniqueness is case-insensitive.
Weekdays use ISO numbering (Monday = 1, Sunday = 7).

One-time blocks/unavailability require a date; weekly entries require a weekday
and a starting date, with an optional inclusive ending date. Wall-clock intervals
must end after they start; use separate dated blocks for overnight study sessions.
Library hours support one interval per weekday; missing days are closed/unknown.
Holiday overrides and multiple daily opening intervals need future migrations.

Shift instants use `timestamptz`; the database derives `local_date` from the start
in America/Chicago. Both endpoints must align to quarter hours and duration must
be positive and at most four elapsed hours. ASSIGNED and BOOKED are active states:
they require a member and enforce one responsibility per member per local start
date. A GiST exclusion constraint additionally prevents overlaps across midnight;
adjacent intervals may touch. This uses PostgreSQL's `btree_gist` extension, which
the migration user must be permitted to install. BOOKED requires the actual room
and confirmation timestamp. Cancellation retains booking details for follow-up.
Shift updates maintain `updated_at` through a database trigger.

Study-block creation validates library hours, active rooms, quarter-hour boundaries,
and backup rooms differing from the preferred room. Its generated shifts cover the
block exactly. Assignment validates member availability, active membership, daily
responsibility limits, overlaps, and weekly workload. Recurring schema support does not implement recurrence.
`PlanningSchemaTests` checks the migration and key database invariants against
isolated PostgreSQL, including timezone independence and cross-midnight overlap.

API specification and generation workflow: [contract documentation](../docs/openapi/README.md).

## Real dashboard data

With PostgreSQL running, start the backend using the `dev` profile as above, then
run `npm run dev` from `frontend/` and open http://localhost:5173. Vite proxies
`/api` to the backend on port 8080; no frontend configuration change is needed.
`npm run dev:mock` remains available for frontend-only work.

The dev profile adds `classpath:db/dev` to Flyway's normal migration locations.
Its V1.1 migration inserts fictional members (`example.test` addresses), DEMO-A/B
rooms, example opening hours, an ordered backup room, and three shifts with
BOOKED, ASSIGNED, and UNASSIGNED statuses. Dates are tomorrow in America/Chicago
when this migration first runs. These are illustrative fixtures, not verified
library hours or real reservations. The migration runs once per database: restarting
does not duplicate fixtures, overwrite edits, or advance their dates. Do not switch
a seeded dev database to production; production must use a separate database.
The normal test/default profiles load only the core schema, without fixtures.

`GET /api/shifts` returns all stored shifts as a JSON array, ordered by start time
then ID, with UTC timestamps, planned room code, and nullable member name. It does
not filter to upcoming shifts. It requires a member session (401 otherwise). Database read
failures return a generic 500 ProblemDetail without exposing database details.

## Create a study block

`POST /api/study-blocks` creates a one-time block, generates shifts, and assigns
eligible members in one transaction. Use real IDs from your local database; IDs are not guaranteed to be
1 and 2. The date and times are local to America/Chicago. For example:

```json
{
  "date": "2026-11-10",
  "startTime": "12:00",
  "endTime": "21:00",
  "preferredRoomId": 1,
  "backupRoomIds": [2]
}
```

Choose a future date when trying this example. The response is `201` with the
saved block (including ID and ordered backup IDs). The three generated 3-hour
shifts then appear in `GET /api/shifts` and the dashboard after refreshing, with
ASSIGNED members where eligible and UNASSIGNED otherwise.
Backups may be omitted or null for no backups. Invalid requests return `400`
ProblemDetail. Missing library hours are treated as unavailable. Times must be
on the same date, fit known hours, and be unambiguous, existent local times.
Midnight closing in library hours is supported, but requests currently use
00:00–23:45 quarter-hour wall-clock times; overnight blocks require separate dates.

The splitter minimizes the number of shifts and distributes quarter-hour slots
evenly; when division is uneven, earlier shifts get the extra slots. Each shift
is at most four elapsed hours. Repeating internal generation for the same block
does not add duplicate shifts or reset assignments/bookings. Repeating the POST
itself creates a new block; it is not an idempotent create API. Editing, recurring
generation and the planning form are separate future work. Owner-only release and
immediate reassignment are implemented as described below.

Tests cover every quarter-hour duration through 24 hours, the architecture's
examples, API validation, timezone conversion, repeated generation, and rollback
of the entire block/backup/shift write when shift insertion fails.


## Member assignment

`AssignmentService` runs after generation and can be retried internally for a block.
It processes only future UNASSIGNED shifts in start-time/ID order; it never resets
ASSIGNED, BOOKED, MISSED, CANCELLED, or already-started shifts. Existing assignments
across blocks take precedence; this is not a global schedule rebalancer.

Eligible members must be active, have no ASSIGNED/BOOKED shift starting on that
America/Chicago local date, have no overlapping active shift (including across
midnight), and have no overlapping unavailability anywhere in the interval. Touching
endpoints do not overlap. One-time and weekly unavailability are supported; weekly
valid-from/until dates are inclusive. Unavailability ending at 24:00 is supported.
On DST fallback, an ambiguous unavailable interval conservatively covers from the
first start occurrence to the last end occurrence; nonexistent spring-forward
boundaries clamp to the transition instant.

Choose the fewest active shifts in the Monday–Sunday Chicago week, then the fewest
elapsed seconds, then the lowest member ID. Count ASSIGNED and BOOKED only, including
past active shifts in that week; allocate a cross-week shift's full duration to its
local start-date week. Re-query after each assignment so earlier choices affect later
ones. When no member qualifies, the shift remains UNASSIGNED with no member.

PostgreSQL transaction advisory lock `730215011` serializes generation/assignment
across application instances for the single v1 study group. Acquire it before block
or shift row locks. Members and existing unavailability rows are share-locked while
selecting candidates. The existing unique/exclusion constraints remain the final
protection for daily limits and overlaps. This serializes short planning writes,
not dashboard reads. Release uses the same lock. Future member management and availability mutation
services must acquire this advisory lock before changing assignment inputs;
there is currently no API for those operations. Direct SQL writes do not participate
in this application coordination. Any assignment failure rolls back the entire
block, backups, generated shifts, and assignments.

No public assignment endpoint or authentication bypass is added. Members still
come from pre-existing database records (fictional dev fixtures locally); this does
not implement registration, invitations, or magic-link authentication.


## Release and immediate reassignment

`POST /api/shifts/{id}/release` returns 204 with no body after a successful release.
The controller obtains the member email from a trusted `Principal.getName()`; there
is no client-selected member ID or header identity in production. The principal must
map to an active member's primary email (case-insensitive) and own the shift. Missing
identity returns 401, inactive/unknown membership or wrong ownership 403, a missing
shift 404 for an active member, and an owned shift in an invalid state/time 409.
GET never releases anything.

Only future ASSIGNED shifts are releasable. BOOKED, MISSED, CANCELLED, started,
past, and unowned shifts are left untouched. A booked reservation must be handled
through the library's cancellation process; this endpoint never cancels in LibCal
or changes booking metadata/cancellation flags on rejected requests.

In one transaction, take the shared assignment lock before row locks, clear the
assignment, then retry only that shift with the releasing member excluded. All
existing eligibility and fairness rules apply. No eligible replacement is a normal
success: leave UNASSIGNED with a null member. Any reassignment failure rolls back
the entire release, retaining the original owner and timestamps. Duplicate or
concurrent retries cannot release the replacement owner's shift. The exclusion is
not stored and does not bar future assignments on other days (or future independent
assignment attempts). Refresh `GET /api/shifts` after success to see the new owner.

The principal comes from the magic-link session (see Authentication below) and is
the member's primary email. Do not substitute request headers, query parameters, or
JSON identity for that principal. HTTP tests sign in through the real `/login/ott`
flow (`TestHttp`); a separate test proves forged identity headers/body do not
authenticate. A member deactivated after signing in gets 401 on the next request.

Tests exercise successful replacement, no eligible replacement, ownership/state/time
checks, unchanged booking data, immediate-only exclusion, unchanged unrelated shifts,
weekly fairness, rollback on failed replacement, duplicate concurrent release, and
HTTP status/ProblemDetail responses. Existing assignment tests continue to cover
eligibility, overlapping reservations, local-date/week boundaries, and DST.


## Email

Services send mail through the `EmailSender` interface (`email` package) with an
`EmailMessage` of recipient, subject, and plain-text body. Inject the interface, never
a concrete sender. Delivery failures surface as `EmailDeliveryException` so future
callers (notification outbox, magic links) can record them.

`LoggingEmailSender` is active for every profile except `prod`: `dev`, `test`, and runs
with no profile log the message at INFO instead of sending it, so local runs and tests
never contact a mail server, even if mail properties are set.

`SmtpEmailSender` is active only for the `prod` profile and sends plain-text mail through
Gmail SMTP (STARTTLS on port 587) using a Google app password for the project account.
`application-prod.properties` reads every value from the environment:

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `SMTP_USERNAME` | yes | — | Gmail address used to sign in |
| `SMTP_PASSWORD` | yes | — | Google app password (not the account password) |
| `EMAIL_FROM` | no | `SMTP_USERNAME` | From address on outgoing mail |
| `SMTP_HOST` | no | `smtp.gmail.com` | SMTP server |
| `SMTP_PORT` | no | `587` | SMTP port (STARTTLS required) |

Fake example only — never commit real values:

```sh
export SMTP_USERNAME=studyroombooker.example@gmail.com
export SMTP_PASSWORD=abcd-efgh-ijkl-mnop
export EMAIL_FROM=studyroombooker.example@gmail.com
```

Startup under `prod` fails if the username, password, or from address is blank, rather
than failing on the first send. SMTP timeouts are 10 seconds. The mail health check is
disabled so `/actuator/health` does not depend on Gmail availability. Delivery errors
are wrapped in `EmailDeliveryException`.


## Authentication

Active members sign in with a magic link (ADR 0001); there are no passwords.

1. The React app calls `GET /api/session` (public). It reports `authenticated`, `email`,
   and `name`, and sets the `XSRF-TOKEN` cookie.
2. `POST /ott/generate` with form field `username=<email>` always returns 204 with no
   body. Only active members (matched case-insensitively) get a token and an email,
   at most one per 60 seconds per member. The email is sent off the request thread,
   so neither the response nor its timing reveals membership.
3. The email links to `${APP_BASE_URL}/login/magic?token=...`, a React page that shows
   **Continue**. Opening the link (GET) never consumes the token; the backend has no GET
   sign-in handler at all, so email scanners such as Safe Links are harmless.
4. Continue sends `POST /login/ott` with form field `token`. Success returns 204 and
   starts a session; an invalid, expired (15 minutes), reused, or inactive-member
   token returns 401 ProblemDetail.
5. `POST /logout` returns 204 and ends the session.

Tokens are 32 random bytes (base64url). Only their SHA-256 hash is stored in
`login_token` (Flyway `V2`); consuming deletes the row atomically, so concurrent
submits cannot both succeed. Expired rows are deleted on the next request for a link.

Every `/api/**` route except `GET /api/session` requires a session; missing sessions
get 401 ProblemDetail rather than a redirect. All POST/PUT/DELETE requests need the
`X-XSRF-TOKEN` header echoing the `XSRF-TOKEN` cookie (403 otherwise); Spring Security
rotates it on sign-in. Session cookies are `HttpOnly` and `SameSite=Lax`, plus `Secure`
under `prod`, and last 7 days. Sessions live in memory, so a backend restart signs
everyone out. A member deactivated after sign-in loses the session on the next request.

| Property | Env var | Default |
|---|---|---|
| `app.base-url` | `APP_BASE_URL` | `http://localhost:5173` (required under `prod`) |
| `app.auth.login-token-ttl` | — | `15m` |
| `app.auth.resend-cooldown` | — | `60s` |

**Local sign-in:** under `dev`, `LoggingEmailSender` writes the magic link to the
backend log. Request a link for a dev-seed member (for example `genaro@example.test`)
from `http://localhost:5173/login`, copy the link from the log, and press Continue.

Not implemented: rate limiting by IP, member invitation/management UI, and persistent
(JDBC) sessions.


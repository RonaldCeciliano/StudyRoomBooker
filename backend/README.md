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
`service`, and `repository` packages establish the conventional layers. All
repositories use plain JDBC (`JdbcTemplate`); the JPA starter is on the classpath
(it supplies the transaction manager) but no entities exist. Hibernate schema
generation is disabled. Flyway applies the migrations in
`src/main/resources/db/migration/` on startup.

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

Startup under `prod` fails if the username or password is blank (a blank `EMAIL_FROM`
falls back to `SMTP_USERNAME`), rather
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


## Marking a shift booked

After reserving the room on LibCal (`https://uark.libcal.com/reserve`; there is no
booking-specific deep link), the assigned member calls `POST /api/shifts/{id}/booked`
with an optional JSON body `{"bookedRoomId": 2}`. It returns 204 and sets `status =
BOOKED`, `booked_room_id`, and `booked_at`. This is group tracking based on the member's
confirmation; nothing contacts or verifies LibCal.

Only the signed-in assigned member may call it (401 without a session, 403 for anyone
else, 404 for a missing shift). The shift must be ASSIGNED or BOOKED and not yet ended
(409 otherwise); a shift in progress can still be confirmed. `bookedRoomId` must be the
planned room or one of the block's active backup rooms (400 otherwise). Without it, the
current booked room is kept, defaulting to the planned room. Repeating the call on a
BOOKED shift corrects the room and keeps the original `booked_at`. BOOKED shifts still
count toward fairness and cannot be released.

`GET /api/shifts` now also returns `bookedRoomCode`, `assignedToMe` (the signed-in
member is the assignee), and `bookingRooms` (planned room, then active backups) so the
dashboard can show Book on LibCal, Mark Booked, and Release on the member's own shifts.

## Listing and editing study blocks

`GET /api/study-blocks` lists one-time blocks dated today or later (America/Chicago),
soonest first. `GET /api/rooms` lists active rooms ordered by code for planning forms.

`PUT /api/study-blocks/{id}` takes the same body and validation as create and returns
the updated block. Only blocks that have not started can be edited (409 otherwise); past
shifts are never rewritten. In one transaction, under the shared assignment lock:

- **Date or times changed:** BOOKED shifts become CANCELLED with
  `libcal_cancel_required = true` (the real LibCal reservation still exists and must be
  cancelled through the library's process); UNASSIGNED and ASSIGNED shifts are removed;
  shifts are regenerated and reassigned with the usual fairness rules.
- **Only rooms changed:** shifts, assignments, and bookings are kept; unbooked shifts move
  to the new preferred room, and backup rooms are replaced in the given order.

Migration `V3` makes the (block, start) uniqueness ignore CANCELLED shifts, so a cancelled
booked shift does not block regenerating a shift at the same start. `GET /api/shifts`
exposes `libcalCancelRequired` so the dashboard tells the member to cancel on LibCal.
Deleting study blocks is not implemented.

## Member unavailability

Members manage only their own entries; identity comes from the session principal and
there is no member ID in the path.

- `GET /api/me/unavailability` lists entries (one-time first, then weekly).
- `POST /api/me/unavailability` adds `{"kind":"ONE_TIME","date":"2026-11-10","startTime":"09:00","endTime":"12:30"}`
  or `{"kind":"WEEKLY","weekday":2,"startTime":"18:00","endTime":"24:00","validUntil":"2026-12-15"}`
  and returns 201. Times are America/Chicago `HH:MM`; `endTime` may be `24:00`.
  One-time dates must be today or later. Weekly `weekday` is 1 (Monday) to 7 (Sunday);
  `validFrom` defaults to today and `validUntil` is optional and inclusive.
- `DELETE /api/me/unavailability/{id}` returns 204, or 404 for missing entries and
  other members' entries.

Writes take the shared assignment advisory lock, as required for assignment inputs.
Entries affect future assignment only: shifts already assigned are not changed, and the
member can release them from the dashboard.

## Notifications and booking reminders

Notifications are stored in the `notification` outbox (Flyway `V4`) before they are sent,
with one row per shift, member, and type (`REMINDER`; `NUDGE` and `GROUP_ALERT` are
reserved). `NotificationScheduler` calls `NotificationService.runOnce()` every
`app.notifications.interval` (default `5m`, first run after `app.notifications.initial-delay`,
default `30s`). Each run is self-healing and works out what is due now:

1. **Enqueue.** Every future ASSIGNED shift whose America/Chicago date is at most 14 days
   away gets a `REMINDER` for its current member. LibCal's 14-day window is rolling, but
   whether a date opens at midnight or at the matching time of day is unverified (ADR
   0005), so reminders go out from the start of that day and tell the member to try again
   later that day if the slot is not open yet.
2. **Send.** Due `PENDING`/`FAILED` rows are claimed with `FOR UPDATE SKIP LOCKED`, so
   overlapping or duplicate runs never send the same reminder twice. The reminder lists the
   room, backup rooms, date, time, the Book Room link (`https://uark.libcal.com/reserve`),
   and the app URL (`APP_BASE_URL`) for marking the shift booked.
3. **Retry.** A failed send is `FAILED` with `last_error` and retries after 5, 10, 20, then
   40 minutes; after 5 attempts `next_attempt_at` is cleared and it stops.
4. **Skip stale reminders.** A reminder whose shift was released, booked, cancelled, or
   started, or whose member was deactivated, is marked `FAILED` ("Not sent: ...") without
   sending. A reassigned shift gets a new reminder for its new member.

Delivery is at-least-once: if the process stops after the mail server accepts a message but
before the row is marked `SENT`, the next run can send it again. One backend instance is
assumed; consider ShedLock if more are added (the outbox itself stays duplicate-safe).
Tests set `app.scheduler.enabled=false` and call `runOnce()` with a controllable `Clock`.
Before the outbox, each run marks ASSIGNED shifts that have **ended** without being marked
booked as MISSED (`MissedShiftService`, under the assignment lock). The end time is used
because members may confirm a booking until the shift ends. Following the assignment rules
from #53, MISSED shifts do not count toward weekly fairness or the one-per-day limit. No
alert is sent yet (`NUDGE`/`GROUP_ALERT` remain reserved).

## Other planning endpoints

- `GET /api/study-blocks/{id}` returns one block (404 if missing or deleted).
- `DELETE /api/study-blocks/{id}` deletes a block that has not started (409 otherwise). BOOKED
  shifts become CANCELLED with `libcal_cancel_required`, unbooked shifts are removed, and the
  block row is removed, or marked `cancelled_at` (Flyway `V5`) and hidden when shifts must be
  kept. Deleted blocks cannot be edited.
- `GET /api/shifts/{id}` returns one shift in the `GET /api/shifts` shape.
- `GET /api/members` lists active members' `id` and `name`; emails are not exposed.

## Email action links

Each booking reminder includes an **I booked it** link to `${APP_BASE_URL}/actions/booked?token=...`
(ARCHITECTURE.md "Action Tokens"). The token is 32 random bytes (base64url), stored only as a
SHA-256 hash in `action_token` (Flyway `V6`), bound to one member, one shift, and the
`MARK_BOOKED` action, and it expires when the shift ends. Each reminder issues its own token;
a send that fails deletes its token.

- `GET /api/actions/booked?token=...` (public) previews the shift and never consumes the token,
  so email scanners are harmless. The React page shows **Confirm Booked**.
- `POST /api/actions/booked` with `{"token": "..."}` (public, CSRF required) consumes the
  token and marks the shift booked in its planned room as the token's member, with the same
  rules as `POST /api/shifts/{id}/booked`. If that is rejected (released, cancelled, ended),
  the transaction rolls back and the link stays usable. Used, expired, or unknown tokens get 404.

No sign-in is needed for the link, so it works from a phone's mail app. Members who booked
a backup room use the dashboard instead.

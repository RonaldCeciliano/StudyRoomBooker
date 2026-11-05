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
block exactly. Assignment still needs to validate member availability and active
membership. Recurring schema support does not implement recurrence.
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
not filter to upcoming shifts. Authentication is still pending. Database read
failures return a generic 500 ProblemDetail without exposing database details.

## Create a study block

`POST /api/study-blocks` creates a one-time block and its unassigned shifts in one
transaction. Use real IDs from your local database; IDs are not guaranteed to be
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
shifts then appear in `GET /api/shifts` and the dashboard after refreshing.
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
generation, member assignment, and the planning form are separate future work.

Tests cover every quarter-hour duration through 24 hours, the architecture's
examples, API validation, timezone conversion, repeated generation, and rollback
of the entire block/backup/shift write when shift insertion fails.

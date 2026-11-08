# API contract

`POST /api/shifts/{id}/release` atomically releases an owned future ASSIGNED shift
and attempts reassignment, excluding the releasing member for this attempt only.
It returns 204 with no body; refresh the shift list to read the outcome. The release
operation requires a magic-link session. See the backend README for ownership, state,
error, and concurrency behavior.

Authentication (ADR 0001): `GET /api/session` (public) reports the session and sets the
`XSRF-TOKEN` cookie; `POST /ott/generate`, `POST /login/ott`, and `POST /logout` use
form bodies. Every write must send `X-XSRF-TOKEN`. Every other `/api` route requires a
session (401 otherwise). The backend README documents the full flow.

`POST /api/study-blocks` atomically creates a one-time study block, splits it into
shifts, and assigns eligible active members. Shifts without eligible members stay
UNASSIGNED. Its request/response schemas are in the same specification;
`StudyBlockApiTests` covers creation, validation, rollback, and repeated internal
generation. `AssignmentServiceTests` covers eligibility, weekly fairness, availability,
DST, and concurrent creates. Repeating POST creates a separate block. See the backend README for
a request example and current planning limitations.

`studyroombooker.yaml` is the authoritative contract for Issue #7 and ADR 0006.
The backend implements `GET /api/shifts` against PostgreSQL. There are no query parameters, pagination, filtering, or envelope.
Successful responses are `200 application/json` arrays, including `[]`.
All Shift properties are required; `memberName` and `bookedRoomCode` are nullable.
`assignedToMe` reflects the signed-in member, and `bookingRooms` lists the planned room
then active backups. `POST /api/shifts/{id}/booked` records the owner's LibCal
confirmation (group tracking only, never proof of a reservation).
Timestamps are UTC ISO-8601 instants ending in `Z` (optional fractional seconds),
while the dashboard displays America/Chicago. IDs are integers; no new numeric
bounds or database ID strategy are imposed by this foundation task.

Statuses are UNASSIGNED, ASSIGNED, BOOKED, MISSED, and CANCELLED.
BOOKED is group tracking based on user confirmation, not independent LibCal proof.
Examples match the existing MSW fixtures. Server errors include a representative
500; the default error response describes other unsuccessful responses as
`application/problem+json`. No error extensions are introduced. The five existing
ProblemDetail fields remain optional.

## Generate and verify

From the repository root:

```sh
npm ci --prefix docs/openapi
```

Then from `frontend/`:

```sh
npm run api:validate
npm run api:generate
npm run api:check
npm test
npm run build
npm run lint
```

Tooling is locked in this directory's package-lock.json. openapi-typescript 7.13.0
requires a TypeScript 5 peer, so its compiler dependency is isolated here;
the application retains TypeScript 6. `api:check` detects stale generated output
without rewriting it. Commit the specification and generated schema.ts together.
Never edit generated types by hand. These are static types, not runtime validators.
The minimal Redocly rules validate the document and examples. Security declaration
linting stays disabled because the public auth endpoints intentionally declare none.

## Backend documentation

springdoc 3.1.1 builds against Spring Boot 4.1.0; this repository uses 4.1.1.
Compatibility and documentation serving are covered by OpenApiDocumentationTests.
Sources: https://github.com/springdoc/springdoc-openapi/blob/v3.1.1/pom.xml
and https://springdoc.org/ (custom specification configuration).
Generator command reference: https://openapi-ts.dev/cli.

Maven copies this specification into `static/openapi` during resource processing;
there is no maintained second copy. Swagger UI at `/swagger-ui/index.html` loads
`/openapi/studyroombooker.yaml`. Try-it-out remains disabled. `/v3/api-docs` is runtime-generated documentation of implemented routes,
not the authoritative source, and must not overwrite the committed contract.
Rebuild/restart the backend after changing the specification.

`ShiftsApiTests` verifies the database-backed HTTP response shape, UTC timestamps,
nullable members, empty results, and safe ProblemDetail database failures. Type freshness does not prove backend response conformance.
Contract changes require Ronald/Genaro review before regeneration.

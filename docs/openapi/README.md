# API contract

`studyroombooker.yaml` is the authoritative contract for Issue #7 and ADR 0006.
It documents the future `GET /api/shifts` endpoint; the backend endpoint is not
implemented. There are no query parameters, pagination, filtering, or envelope.
Successful responses are `200 application/json` arrays, including `[]`.
All six Shift properties are required; only `memberName` is nullable.
Timestamps are UTC ISO-8601 instants ending in `Z` (optional fractional seconds),
while the dashboard displays America/Chicago. IDs are integers; no new numeric
bounds or database ID strategy are imposed by this foundation task.

Statuses are UNASSIGNED, ASSIGNED, BOOKED, MISSED, and CANCELLED.
BOOKED is group tracking based on user confirmation, not independent LibCal proof.
Examples match the existing MSW fixtures. Server errors include a representative
500; the default error response describes other unsuccessful responses as
`application/problem+json`. No authentication implementation or error extensions
are introduced. The five existing ProblemDetail fields remain optional.

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
linting is disabled because authentication is explicitly outside this initial scope.

## Backend documentation

springdoc 3.1.1 builds against Spring Boot 4.1.0; this repository uses 4.1.1.
Compatibility and documentation serving are covered by OpenApiDocumentationTests.
Sources: https://github.com/springdoc/springdoc-openapi/blob/v3.1.1/pom.xml
and https://springdoc.org/ (custom specification configuration).
Generator command reference: https://openapi-ts.dev/cli.

Maven copies this specification into `static/openapi` during resource processing;
there is no maintained second copy. Swagger UI at `/swagger-ui/index.html` loads
`/openapi/studyroombooker.yaml`. Try-it-out is disabled while the domain endpoint
is absent. `/v3/api-docs` is runtime-generated documentation of implemented routes,
not the authoritative source, and must not overwrite the committed contract.
Rebuild/restart the backend after changing the specification.

Future endpoint work must implement this contract and add request/response
conformance tests. Type freshness does not prove backend response conformance.
Contract changes require Ronald/Genaro review before regeneration.

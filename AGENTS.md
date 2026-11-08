# Working on StudyRoomBooker

Rules for every contributor, human or AI. [ARCHITECTURE.md](ARCHITECTURE.md) is the source
of truth for scope and design; [docs/memory.md](docs/memory.md) is the living status board.

## Picking up work

1. Read `docs/memory.md` for current status and open questions.
2. Pick an open issue (labels `ready` first). Comment that you are taking it.
3. Branch from the latest `main`: `git switch main && git pull && git switch -c feature/<n>-<short-name>`.
   If your work depends on an unmerged PR, branch from that PR's branch and say so in your PR.
4. Keep one issue per pull request.

## Commands

| Task | Command |
|---|---|
| Frontend check (same as CI) | `cd frontend && npm ci && npm --prefix ../docs/openapi ci && npm run check` |
| Frontend dev server (real API) | `cd frontend && npm run dev` |
| Frontend with fake API (MSW) | `cd frontend && npm run dev:mock` |
| Backend tests (same as CI; Docker must be running) | `cd backend && ./mvnw test` |
| Local database | `docker compose -f docker-compose.dev.yml up -d --wait` |
| Backend dev server | `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` |
| Regenerate API types after editing the spec | `cd frontend && npm run api:generate` |

Local sign-in: under `dev`, magic links are written to the backend log (see the backend README).

## Merging

- Open a PR using the template: `Fixes #<n>` and a real test plan are required.
- The other developer reviews. Contract changes need Ronald/Genaro review.
- CI (frontend `npm run check`, backend `./mvnw test`) must pass. Never merge on red.
- After merging, update `docs/memory.md` if status, open questions, or decisions changed.

## Standing rules

- **Contract first.** Change `docs/openapi/studyroombooker.yaml` and the regenerated
  `frontend/src/api/generated/schema.ts` in the same PR. Never edit generated types by hand.
- **Time.** Store instants in UTC; display and reason in America/Chicago (ADR 0002). Inject
  `Clock`; never call `Instant.now()` in services.
- **Security.** Identity comes only from the session principal, never from request bodies,
  headers, or query parameters. GET never consumes tokens or changes state. Writes need CSRF.
- **LibCal.** BOOKED is the member's confirmation, not proof. Link only to
  `https://uark.libcal.com/reserve`; do not invent booking deep links.
- **Assignment inputs.** Code that changes members, unavailability, blocks, or assignments
  takes the shared assignment advisory lock first (backend README).
- **Data.** Fake development data and credentials only. Never commit secrets or real member data.
- **Scope.** No unrelated changes, no empty scaffolding, no direct pushes to `main`.
- **Tests.** Backend database behavior is tested against PostgreSQL via Testcontainers, not
  mocks. Frontend tests use MSW; unhandled requests fail the test.

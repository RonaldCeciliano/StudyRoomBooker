# StudyRoomBooker

StudyRoomBooker is a collaborative web application for coordinating University of Arkansas Mullins Library study-room reservations.

## Phase 1 goal

Plan study blocks, split them into shifts of no more than four hours, fairly assign members, send booking reminders, and track reservations on a shared dashboard. Assigned members complete reservations manually through UARK LibCal and then mark their shifts as booked.

A possible Phase 2 Chrome extension depends on University Libraries approval. It is not required for Phase 1.

## Current status

**Phase 1 is implemented and merged to `main` (#68–#80); deployment is next.**
Members sign in with a magic link, plan study blocks, set their unavailability, and see a
dashboard of shifts. Shifts are split and fairly assigned automatically; owners can mark
them booked (from the dashboard or an emailed "I booked it" link) or release them. A
scheduler emails booking reminders when a date enters LibCal's 14-day window and marks
unbooked shifts MISSED once they end. The production stack (Compose, Caddy, daily backups)
is ready; what remains needs people: the ops tickets (#49–#51) and the Oracle VM. See [docs/memory.md](docs/memory.md) for the live board and the backend and
frontend READMEs for local startup.

The target operating cost is **$0**.

## Technology stack

| Area | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot, Spring REST, Spring Security, JDBC (`JdbcTemplate`), Maven, Spring Scheduler, springdoc-openapi |
| Frontend | React, TypeScript, Vite, TanStack Query, openapi-typescript |
| Database | PostgreSQL, Flyway |
| Testing | JUnit, Mockito, Testcontainers, Vitest, React Testing Library, MSW |
| Infrastructure | Docker, Docker Compose, Caddy, GitHub Actions; Oracle Cloud VM (planned) |
| Email | EmailSender interface, Gmail SMTP, LoggingEmailSender for development |

## High-level architecture

The application is a modular monolith. Caddy provides HTTPS and same-origin routing.

```text
Internet -> Caddy -> React frontend
                 -> Spring Boot API -> PostgreSQL
                                    -> EmailSender -> Gmail SMTP
```

Backend business logic belongs in services, using controller, service, and repository layers.

## Documentation

- [Architecture & Implementation Plan](ARCHITECTURE.md) — source of truth for scope, design, milestones, and responsibilities.
- [Architecture Decision Records](docs/adr/) — the ten decisions documented in the plan.
- [Production deployment](docs/deployment.md) — Compose, Caddy, environment, and operations.

## Collaboration workflow

See [AGENTS.md](AGENTS.md) for commands and standing rules.

1. Pick an issue from [docs/memory.md](docs/memory.md) or the issue list.
2. Branch from the latest `main`: `feature/<issue>-<short-name>`.
3. Implement and test: `cd frontend && npm run check`, `cd backend && ./mvnw test`.
4. Push and open a pull request with `Fixes #<n>` and a test plan.
5. The other developer reviews; CI must pass before merging.

Avoid unrelated changes directly on `main`. Keep secrets out of Git and use only fake
development credentials and data.

## Team

- Ronald Ceciliano
- Genaro

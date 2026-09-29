# StudyRoomBooker

StudyRoomBooker is a planned collaborative web application for coordinating University of Arkansas Mullins Library study-room reservations.

## Phase 1 goal

Plan study blocks, split them into shifts of no more than four hours, fairly assign members, send booking reminders, and track reservations on a shared dashboard. Assigned members complete reservations manually through UARK LibCal and then mark their shifts as booked.

A possible Phase 2 Chrome extension depends on University Libraries approval. It is not required for Phase 1.

## Current status

**Implementation has not started.** The project is in Milestone -1: pre-implementation research and documentation review.

Before Milestone 0, the team will review the architecture and ADRs, observe LibCal booking-window behavior, investigate booking URLs, prepare email delivery, investigate deployment and DNS, and contact the library about Phase 2.

The target operating cost is **$0**.

## Planned technology stack

| Area | Technologies |
| --- | --- |
| Backend | Java 21, Spring Boot, Spring REST, Spring Security, Spring Data JPA, Hibernate, Maven, Spring Scheduler, springdoc-openapi |
| Frontend | React, TypeScript, Vite, TanStack Query, openapi-typescript |
| Database | PostgreSQL, Flyway |
| Testing | JUnit, Mockito, Testcontainers, Vitest, React Testing Library, MSW |
| Infrastructure | Docker, Docker Compose, Caddy, GitHub Actions; planned Oracle Cloud VM |
| Email | EmailSender interface, Gmail SMTP, LoggingEmailSender for development |

## High-level architecture

The application will begin as a modular monolith. Caddy will provide HTTPS and same-origin routing.

```text
Internet -> Caddy -> React frontend
                 -> Spring Boot API -> PostgreSQL
                                    -> EmailSender -> Gmail SMTP
```

Backend business logic belongs in services, using controller, service, and repository layers.

## Documentation

- [Architecture & Implementation Plan](ARCHITECTURE.md) — source of truth for scope, design, milestones, and responsibilities.
- [Architecture Decision Records](docs/adr/) — the ten decisions documented in the plan.

## Collaboration workflow

1. Review the current milestone and create an issue.
2. Create a feature branch, such as `feature/study-blocks`.
3. Implement the scoped change and test it.
4. Push the branch and open a pull request.
5. Have the other developer review it.
6. Merge after required CI checks pass.

Avoid unrelated changes directly on `main`. Create application directories and configuration incrementally as their milestones begin. Keep secrets out of Git and use only fake development credentials and data.

## Team

- Ronald Ceciliano
- Genaro

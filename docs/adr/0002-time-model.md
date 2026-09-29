# 0002 — PostgreSQL timestamptz, America/Chicago, and injected Clock

Status: Documented in the architecture plan; pending team review before implementation.

## Context

Reservation planning follows local library time, while persisted timestamps may represent actual moments. Scheduler and time-based tests need deterministic time.

## Decision

Use `America/Chicago` as the primary application timezone. Store timestamps representing actual moments as PostgreSQL `timestamptz`. Inject a Java `Clock` into time-dependent services.

## Consequences

Business logic should use the injected clock instead of calling the system clock throughout the application. Tests must cover DST and timezone cases.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#time-model) is the source of truth.

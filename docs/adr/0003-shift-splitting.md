# 0003 — Shift splitting on 15-minute boundaries with a 4-hour maximum

Status: Documented in the architecture plan; pending team review before implementation.

## Context

Library reservations use 15-minute intervals and allow a person up to four hours per day. Study blocks may be longer than a single reservation.

## Decision

Split study blocks into shifts no longer than four hours, aligned with 15-minute intervals. Prefer reasonably even splits and validate blocks against known library hours. Generation must be deterministic and idempotent.

## Consequences

A 12-hour block becomes three 4-hour shifts; a 9-hour block becomes three 3-hour shifts; a 5-hour block becomes two 2.5-hour shifts. Repeated generation must not create duplicates. Service checks and database constraints protect shift invariants.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#study-block-to-shift-generation) is the source of truth.

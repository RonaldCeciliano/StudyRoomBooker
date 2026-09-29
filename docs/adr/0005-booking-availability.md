# 0005 — Booking availability is 14 days in advance

Status: Documented in the architecture plan; pending team review before implementation.

## Context

The application must determine when to remind the assigned member to reserve a room.

## Decision

Use a 14-day advance-booking window.

## Consequences

Do not assume availability is released at midnight or at the exact reservation time. Observe LibCal behavior before refining the scheduling behavior. Scheduler runs should find due actions that have not already been completed.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#1-libcal-booking-window) is the source of truth.

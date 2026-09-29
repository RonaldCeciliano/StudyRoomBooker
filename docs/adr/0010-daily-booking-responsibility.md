# 0010 — One active booking responsibility per member per local day

Status: Documented in the architecture plan; pending team review before implementation.

## Context

Assignments must respect daily booking limits, availability, and overlapping reservations.

## Decision

Give each member at most one active booking responsibility per local date. Exclude members who already have an active shift that day, are unavailable, or conflict with another assigned reservation. Protect the daily and overlap invariants in application services and PostgreSQL.

## Consequences

Among eligible members, prefer the fewest shifts that week, then the fewest hours, then stable member ID. Leave a shift `UNASSIGNED` if nobody is eligible. Immediate reassignment excludes the member who released the shift without permanently excluding them on other days.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#assignment-algorithm) is the source of truth.

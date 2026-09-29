# 0004 — Shift status represents booking state; notifications stored separately

Status: Documented in the architecture plan; pending team review before implementation.

## Context

StudyRoomBooker tracks the group's reservation responsibilities but does not control LibCal. Notifications need independent delivery and retry tracking.

## Decision

Use shift statuses `UNASSIGNED`, `ASSIGNED`, `BOOKED`, `MISSED`, and `CANCELLED`. Store notifications before sending them in a separate outbox with `PENDING`, `SENT`, and `FAILED` statuses. Prevent duplicate notifications for the same shift, member, and notification type.

## Consequences

`BOOKED` records user confirmation, not independent proof of a LibCal reservation. Cancelling an affected booked shift sets `libcal_cancel_required = true`; the user must still cancel through the library. Notification attempts and errors are tracked separately from shift status.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#notification-outbox) is the source of truth.

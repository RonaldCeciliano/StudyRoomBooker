# 0001 — Magic-link authentication and session cookies

Status: Documented in the architecture plan; pending team review before implementation.

## Context

Phase 1 needs authentication for invited, active group members without project-specific passwords. Email scanners may automatically open links.

## Decision

Use magic-link authentication and authenticated sessions. Generate a one-time token through `POST /ott/generate`; show a React confirmation page when the link is opened; consume the token through `POST /login/ott` after the user continues. Production session cookies use `HttpOnly`, `Secure`, and `SameSite=Lax`.

## Consequences

GET requests must not consume authentication or action tokens. Only invited, active members may authenticate, and responses should avoid revealing membership for arbitrary email addresses.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#authentication) is the source of truth.

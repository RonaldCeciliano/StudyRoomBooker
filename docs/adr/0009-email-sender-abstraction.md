# 0009 — EmailSender abstraction

Status: Documented in the architecture plan; pending team review before implementation.

## Context

The notification system needs production email delivery and a development option, while allowing the provider to change.

## Decision

Place email delivery behind an `EmailSender` application interface. Use Gmail SMTP through a dedicated project account as the initial production option and `LoggingEmailSender` for development.

## Consequences

The email provider can change without rewriting the notification system. Prepare the project account and verify delivery to a UARK inbox before implementation; keep SMTP credentials out of Git.

## Source

[ARCHITECTURE.md](../../ARCHITECTURE.md#email) is the source of truth.

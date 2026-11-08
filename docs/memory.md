# Living board

Update this file in the PR that changes status, answers a question, or makes a decision.
Last updated: 2026-11-07.

## Status

| Area | State |
|---|---|
| Backend foundation, OpenAPI contract, frontend foundation | Merged (#4, #5, #8) |
| Planning schema, shift generation, fair assignment, release | Merged (#52, #53, #54) |
| EmailSender, LoggingEmailSender, Gmail SMTP sender (`prod`) | Merged (#68, #69) |
| Magic-link sign-in, sessions, CSRF | Merged (#70) |
| Mark booked + dashboard booking/release actions | Merged (#71) |
| Study-block planning page, member unavailability page | Merged (#72, #73) |
| Notification outbox, scheduler, booking reminders | Merged (#74) |
| CI, templates, this board | Merged (#75) |
| Production Compose, Caddy, health checks, daily backups | Merged (#76, #77) |
| Remaining planning endpoints, MISSED marking, emailed "I booked it" links | Merged (#78, #79, #80) |
| Off-box backup copy | Needs an Oracle bucket and keys |
| Ops: project Gmail (#49), DuckDNS (#50), library contact (#51) | Needs a person |
| Required CI status check on `main` | Needs repository admin (Ronald) |

## Open questions

- MISSED now happens when an unbooked shift ends. Should anyone be alerted earlier (NUDGE/GROUP_ALERT), and should MISSED count toward fairness (#53 says no)?
- Exact time LibCal opens a date 14 days ahead (midnight vs. matching time). Reminders
  currently go out at the start of the day and say to retry later that day (ADR 0005).
- Member invitation/management: members are still inserted directly in the database.
- Sessions are in memory, so a backend restart signs everyone out. Acceptable for one instance?

## Roadmap

1. Deploy with `docker-compose.yml` (#47, see docs/deployment.md) once #49/#50 provide
   Gmail credentials and a hostname.
2. Create the Oracle Object Storage bucket and the rclone sync for backups (#48 follow-up).
3. Ops tickets (#49–#51), then a real Gmail delivery test.
4. Alerts for shifts about to be missed; member invitation UI.

## Decision log

| Date | Decision | Where |
|---|---|---|
| 2026-11-07 | Magic-link tokens stored as SHA-256 hashes, single use, 15 min; 60 s resend cooldown | backend README, #70 |
| 2026-11-07 | All `/api/**` except `GET /api/session` require a session; SPA CSRF cookie/header | #70 |
| 2026-11-07 | Booked room defaults to the planned room; may be an active backup | #71 |
| 2026-11-07 | Editing times cancels BOOKED shifts with `libcal_cancel_required`; room-only edits keep shifts | #72 |
| 2026-11-07 | Unavailability affects future assignment only; existing assignments unchanged | #73 |
| 2026-11-07 | Reminders at the start of the day the date enters the 14-day window; 5 attempts with backoff | #74 |
| 2026-09-30 | Rolling 14-day LibCal window; generic reserve link only | ARCHITECTURE.md Milestone -1 |

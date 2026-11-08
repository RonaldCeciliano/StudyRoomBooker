# Living board

Update this file in the PR that changes status, answers a question, or makes a decision.
Last updated: 2026-11-07.

## Status

| Area | State |
|---|---|
| Backend foundation, OpenAPI contract, frontend foundation | Merged (#4, #5, #8) |
| Planning schema, shift generation, fair assignment, release | Merged (#52, #53, #54) |
| EmailSender + LoggingEmailSender | PR #55 |
| Gmail SMTP sender (`prod`) | PR #56 |
| Magic-link sign-in, sessions, CSRF | PR #57 |
| Mark booked + dashboard booking/release actions | PR #58 |
| Study-block planning page (list, create, edit) | PR #59 |
| Member unavailability page | PR #60 |
| Notification outbox, scheduler, booking reminders | PR #61 |
| CI, templates, this board | PR for #30 |
| Production Compose, Caddy, health checks | PR for #47 |
| PostgreSQL backups (daily dump, rotation, restore steps) | PR for #48; off-box bucket needs Oracle access |
| Ops: project Gmail (#49), DuckDNS (#50), library contact (#51) | Needs a person |

PRs #55–#61 are stacked; merge them in order so each retargets to `main`.

## Open questions

- MISSED now happens when an unbooked shift ends. Should anyone be alerted earlier (NUDGE/GROUP_ALERT), and should MISSED count toward fairness (#53 says no)?
- Exact time LibCal opens a date 14 days ahead (midnight vs. matching time). Reminders
  currently go out at the start of the day and say to retry later that day (ADR 0005).
- Member invitation/management: members are still inserted directly in the database.
- Sessions are in memory, so a backend restart signs everyone out. Acceptable for one instance?

## Roadmap

1. Merge the stacked PRs (#55 → #61) and this CI PR.
2. Deploy with `docker-compose.yml` (#47, see docs/deployment.md) once #49/#50 provide
   Gmail credentials and a hostname.
3. Create the Oracle Object Storage bucket and the rclone sync for backups (#48 follow-up).
4. Ops tickets (#49–#51), then a real Gmail delivery test.
5. Alerts for shifts about to be missed; member invitation UI.

## Decision log

| Date | Decision | Where |
|---|---|---|
| 2026-11-07 | Magic-link tokens stored as SHA-256 hashes, single use, 15 min; 60 s resend cooldown | backend README, #57 |
| 2026-11-07 | All `/api/**` except `GET /api/session` require a session; SPA CSRF cookie/header | #57 |
| 2026-11-07 | Booked room defaults to the planned room; may be an active backup | #58 |
| 2026-11-07 | Editing times cancels BOOKED shifts with `libcal_cancel_required`; room-only edits keep shifts | #59 |
| 2026-11-07 | Unavailability affects future assignment only; existing assignments unchanged | #60 |
| 2026-11-07 | Reminders at the start of the day the date enters the 14-day window; 5 attempts with backoff | #61 |
| 2026-09-30 | Rolling 14-day LibCal window; generic reserve link only | ARCHITECTURE.md Milestone -1 |

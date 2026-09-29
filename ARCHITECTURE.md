# StudyRoomBooker — Architecture & Implementation Plan

## Project Overview

StudyRoomBooker is a collaborative web application for coordinating University of Arkansas Mullins Library study-room reservations.

The application will:

- Let the group create planned study blocks.
- Split study blocks into reservation shifts of no more than 4 hours.
- Fairly assign those shifts among group members.
- Track who is responsible for booking each shift.
- Notify the assigned member when it is time to make the reservation.
- Track whether each reservation has been booked.
- Provide a shared dashboard showing the group's study-room schedule.

### Phase 1

StudyRoomBooker plans, assigns, reminds, and tracks reservations.

The actual LibCal reservation is completed manually by the assigned member through the University of Arkansas LibCal website.

### Phase 2

If approved by the University Libraries, a Chrome extension may assist the assigned member with preparing the correct room/date/time inside LibCal.

The user will remain responsible for UARK authentication, accepting any required terms, and completing the final reservation.

### Team

- Ronald Ceciliano
- Genaro

### Budget

Target operating cost: **$0**

---

# Milestone -1 Decisions

These are the three questions that must be resolved before implementation begins.

## 1. LibCal Booking Window

**Decision:** A study-room booking becomes available **14 days in advance**.

StudyRoomBooker will use the 14-day advance-booking window when determining when an assigned member should be reminded to make the reservation.

We do not currently need to assume whether the release occurs at midnight or at the exact reservation time.

The important Phase 1 requirement is:

```text
Reservation Date
      |
      | 14 days earlier
      v
Booking becomes available
      |
      v
Assigned member receives booking reminder
```

---

## 2. LibCal Link Investigation

**Decision:** We need to investigate how LibCal represents reservation information in its URLs before deciding how deep linking will work.

We will give ChatGPT two examples:

### Link A — Normal LibCal Link

Open LibCal normally without selecting a reservation.

Copy the URL.

Example purpose:

```text
Normal LibCal page
No specific booking selected
        |
        v
Copy URL
```

### Link B — Booking-Specific LibCal Link

Navigate through LibCal to a particular:

- Date
- Room
- Start time
- End time

Then copy the URL again.

```text
Select booking information
        |
        v
Date
Room
Start time
End time
        |
        v
Copy resulting URL
```

We will compare **Link A** and **Link B**.

The goal is to determine whether LibCal exposes any useful booking information in the URL, such as:

```text
date
room
start time
end time
location
booking parameters
```

If the booking-specific URL contains reusable parameters, StudyRoomBooker may be able to construct a link that sends the assigned member closer to the correct reservation.

For example:

```text
StudyRoomBooker
      |
      v
Assigned Shift
Room 441D
Oct. 12
6:00 PM - 10:00 PM
      |
      v
Generate LibCal link
      |
      v
User opens LibCal closer to
the correct reservation
```

If LibCal does **not** preserve the booking information in the URL, Phase 1 will simply provide the normal LibCal reservation link along with:

- Assigned room
- Backup rooms
- Reservation date
- Start time
- End time

The user can then enter/select those details manually.

**No deep-link URL structure should be assumed until we test the actual LibCal URLs.**

---

## 3. Phase 2 Chrome Extension Approval

**Decision:** We need to check with the University Libraries before implementing the Chrome extension.

The proposed extension would:

- Not store UARK passwords.
- Not bypass Microsoft SSO.
- Not bypass MFA.
- Not crawl LibCal in the background.
- Not continuously poll LibCal.
- Only run after the user initiates the booking workflow.
- Assist with selecting the intended room/date/time.
- Leave Terms & Conditions acceptance to the user.
- Leave final booking submission to the user.

### If the Library Approves the Extension

Proceed with Phase 2.

```text
StudyRoomBooker
      |
      v
Assigned booking
      |
      v
User clicks Book Room
      |
      v
Chrome Extension
      |
      v
Open LibCal
      |
      v
Prepare room/date/time
      |
      v
User authenticates if needed
      |
      v
User reviews reservation
      |
      v
User accepts terms
      |
      v
User submits reservation
```

### If the Library Does Not Approve the Extension

Do not attempt to work around the restriction using:

- Selenium
- Playwright
- Stored UARK credentials
- Automated Microsoft SSO
- Automated MFA
- Unattended browser automation

Instead, Phase 2 becomes an **Enhanced Manual Booking** experience.

```text
Booking becomes available
      |
      v
Assigned member receives reminder
      |
      v
StudyRoomBooker displays:

Room
Backup rooms
Date
Start time
End time
      |
      v
User clicks Book Room
      |
      v
Best available LibCal link
      |
      v
User completes reservation
      |
      v
User returns to StudyRoomBooker
      |
      v
Marks shift BOOKED
```

We can also ask the library whether official **LibCal Spaces API** access is available.

If an institution-approved API is available, that would be the preferred future path for deeper integration.

The Chrome extension is an enhancement and is **not required for StudyRoomBooker Phase 1 to be considered complete**.

---

# Library Rules

The application will be designed around the following Mullins Library study-room rules identified during planning:

- A person may reserve up to 4 hours per day.
- Reservations can be made up to 14 days in advance.
- A person cannot hold overlapping room reservations.
- A person may hold reservations on different days.
- One room is associated with each booking.
- Reservations are available to current UARK affiliates.
- Reservation times use 15-minute intervals.
- The LibCal availability grid can be viewed before authentication.
- UARK authentication occurs during the reservation process.
- Selected reservation times may only be held temporarily during booking.
- Cancellation is handled through the library's reservation process.

StudyRoomBooker must not assume that it controls LibCal reservations.

---

# Technology Stack

## Backend

- Java 21
- Spring Boot
- Spring REST
- Spring Security
- Spring Data JPA
- Hibernate
- Maven
- Spring Scheduler
- springdoc-openapi

## Frontend

- React
- TypeScript
- Vite
- TanStack Query
- openapi-typescript

## Database

- PostgreSQL
- Flyway migrations

## Testing

### Backend

- JUnit
- Mockito
- Testcontainers

### Frontend

- Vitest
- React Testing Library
- Mock Service Worker (MSW)

## Infrastructure

- Docker
- Docker Compose
- Caddy
- GitHub
- GitHub Actions

## Email

Email functionality will be implemented behind an application interface.

Initial production option:

- Gmail SMTP using a dedicated project account

Development option:

- LoggingEmailSender

This allows the email provider to be changed later without rewriting the notification system.

---

# Architecture Style

StudyRoomBooker will begin as a **modular monolith**.

We will not begin with microservices.

The backend will use conventional layers:

```text
Controller
    |
    v
Service
    |
    v
Repository
    |
    v
PostgreSQL
```

Business logic belongs in services rather than controllers.

---

# High-Level Architecture

```text
                    Internet
                       |
                       v
                     HTTPS
                       |
                       v
                     Caddy
                    /     \
                   /       \
                  v         v
            React App   Spring Boot API
                             |
                +------------+------------+
                |                         |
                v                         v
           PostgreSQL                 EmailSender
                                          |
                                          v
                                      Gmail SMTP
```

Caddy will provide same-origin routing.

Example:

```text
/               -> React frontend
/api/*          -> Spring Boot
/ott/*          -> Spring Boot
/login/ott      -> Spring Security
/logout         -> Spring Security
```

Using the same origin simplifies:

- Session cookies
- CSRF protection
- Deployment
- CORS configuration

---

# Authentication

Phase 1 will use **magic-link authentication**.

Users will not create project-specific passwords.

The intended flow:

```text
User enters email
      |
      v
POST /ott/generate
      |
      v
One-time token created
      |
      v
Email sent
      |
      v
User opens magic link
      |
      v
React confirmation page
      |
      v
POST /login/ott
      |
      v
Token consumed
      |
      v
Authenticated session created
```

Authentication should use an:

```text
HttpOnly
Secure
SameSite=Lax
```

session cookie in production.

Only invited and active group members should be able to authenticate.

Responses should avoid revealing whether arbitrary email addresses belong to members.

---

# Magic-Link Safety

Email scanners such as Microsoft Safe Links may automatically open links.

Therefore:

**GET requests must not consume authentication or action tokens.**

Instead:

```text
GET magic link
      |
      v
Display confirmation page
      |
      v
User presses Continue
      |
      v
POST request
      |
      v
Token consumed
```

The same pattern should be used for email action links.

---

# Time Model

Primary application timezone:

```text
America/Chicago
```

Database timestamps representing actual moments should use:

```text
timestamptz
```

Java time-dependent services should use an injected:

```java
Clock
```

rather than directly calling the system clock throughout business logic.

This makes scheduler and time-based tests deterministic.

---

# Core Domain Model

## Member

```text
id
name
email
uark_email
active
created_at
```

---

## Room

```text
id
code
name
capacity
type
active
```

---

## LibraryHours

```text
weekday
opens_at
closes_at
```

---

## MemberUnavailability

Supports:

- Recurring unavailability
- One-time unavailability

---

## StudyBlock

```text
id
kind
date
weekday
valid_from
valid_until
start_time
end_time
preferred_room_id
created_at
```

Study blocks may eventually support:

- One-time blocks
- Weekly recurring blocks

---

## StudyBlockBackupRoom

```text
study_block_id
room_id
priority
```

Backup rooms are ordered.

Example:

```text
Preferred: 441D

Backup 1: 441E
Backup 2: 441C
```

---

# Shift

```text
id
study_block_id
member_id
planned_room_id
starts_at
ends_at
local_date
status
booked_room_id
booked_at
libcal_cancel_required
created_at
updated_at
```

Possible statuses:

```text
UNASSIGNED
ASSIGNED
BOOKED
MISSED
CANCELLED
```

---

# Shift Constraints

Database constraints should protect important invariants.

Examples:

- Shift end must occur after shift start.
- Shift duration cannot exceed 4 hours.
- Shift starts should align to 15-minute boundaries.
- Duplicate shifts for the same study block/start time should not exist.
- A member cannot hold overlapping active shifts.
- A member should not receive more than one active booking responsibility on the same local date.

PostgreSQL constraints should act as a final safety layer even when the application service already checks these conditions.

---

# Study Block to Shift Generation

Study blocks are converted into shifts.

Rules:

- Maximum shift length: 4 hours.
- Shifts should align with 15-minute intervals.
- Prefer reasonably even splits.
- Validate the block against known library hours.

Examples:

```text
12-hour study block

4h
4h
4h
```

```text
9-hour study block

3h
3h
3h
```

```text
5-hour study block

2.5h
2.5h
```

The generator should be deterministic and idempotent.

Running it twice must not create duplicate shifts.

---

# Assignment Algorithm

Assignments are processed chronologically.

For each shift, remove members who:

- Already have an active shift that local day.
- Are unavailable during that time.
- Conflict with another assigned reservation.

Among the remaining members, prefer:

1. Fewest assigned shifts that week.
2. Fewest assigned hours that week.
3. Stable member ID as the deterministic tie-breaker.

If nobody is eligible:

```text
member_id = null
status = UNASSIGNED
```

The assignment logic belongs in:

```text
AssignmentService
```

For the earliest milestone, a simple fake or round-robin assignment service may be used while the rest of the architecture is established.

---

# Releasing an Assignment

An assigned member may release a future shift.

The shift returns to:

```text
UNASSIGNED
```

The assignment service attempts to reassign it.

During immediate reassignment:

- Exclude the member who just released it.
- Exclude anyone who already has an active shift that day.
- Respect unavailability.
- Respect overlap constraints.

Releasing one shift should not permanently prevent that member from receiving another shift on a different day.

---

# Editing Study Blocks

Future unbooked shifts affected by an edit may be cancelled and regenerated.

If an already booked shift is affected:

```text
status = CANCELLED
libcal_cancel_required = true
```

StudyRoomBooker cannot assume that changing its own database cancels the real LibCal reservation.

The user must still cancel the LibCal reservation through the supported library process.

Past shifts should not be rewritten.

---

# Notification Outbox

Notifications should be stored before they are sent.

Example:

```text
notification
------------
id
shift_id
member_id
type
status
attempt_count
last_error
created_at
sent_at
```

Types:

```text
REMINDER
NUDGE
GROUP_ALERT
```

Statuses:

```text
PENDING
SENT
FAILED
```

A uniqueness rule should prevent duplicate notifications for the same:

```text
shift
member
notification type
```

---

# Booking Reminder

A booking reminder should contain:

```text
Study Room Booker

Room: 441D
Backup Rooms: 441E, 441C

Date: October 12
Time: 6:00 PM - 10:00 PM

[Book Room]
```

The destination of **Book Room** will be determined by the LibCal URL investigation.

If we discover a reusable booking-specific URL:

```text
Book Room -> booking-specific LibCal link
```

Otherwise:

```text
Book Room -> normal LibCal reservation page
```

The booking details remain visible in the reminder either way.

---

# Scheduler

Spring Scheduler will periodically check for work.

Initial interval:

```text
Every 5 minutes
```

The scheduler should be **self-healing**.

Instead of assuming it ran at an exact second, each run should determine which actions are currently due and which have not already been completed.

Responsibilities may include:

- Generate upcoming shifts.
- Detect bookings entering their 14-day booking window.
- Create reminder notifications.
- Send pending notifications.
- Retry failed notifications.
- Detect overdue unbooked shifts.
- Mark applicable shifts MISSED.
- Generate later nudges or group alerts if those features remain in scope.

Phase 1 production deployment will initially use one backend instance.

If multiple backend instances are introduced later, distributed scheduler coordination such as ShedLock should be considered.

---

# Booking Confirmation

After manually completing the LibCal reservation, the assigned member returns to StudyRoomBooker and marks the shift as booked.

Example:

```text
POST /api/shifts/{id}/booked
```

The application records:

```text
status = BOOKED
booked_room_id
booked_at
```

This represents the group's tracking state.

It does not independently prove that LibCal accepted the reservation.

---

# Action Tokens

Email actions may use short-lived random tokens.

Properties:

- Cryptographically random
- Single use
- Stored hashed in the database
- Associated with a specific member
- Associated with a specific shift
- Expire automatically
- GET does not mutate state
- POST performs the action

Example token size:

```text
32 random bytes
```

---

# API Design

The backend will expose REST APIs.

Possible endpoints include:

```text
POST   /api/study-blocks
GET    /api/study-blocks
GET    /api/study-blocks/{id}
PUT    /api/study-blocks/{id}
DELETE /api/study-blocks/{id}

GET    /api/shifts
GET    /api/shifts/{id}

POST   /api/shifts/{id}/release
POST   /api/shifts/{id}/booked

GET    /api/members
GET    /api/rooms

GET    /api/dashboard

POST   /ott/generate
POST   /login/ott
POST   /logout
```

API errors should use Spring's:

```text
ProblemDetail
```

where appropriate.

---

# OpenAPI

The REST contract should be documented with OpenAPI.

The OpenAPI specification should be committed to the repository.

The frontend should generate TypeScript API types using:

```text
openapi-typescript
```

This helps keep the Java backend and TypeScript frontend synchronized.

---

# Frontend Architecture

Frontend:

```text
React
TypeScript
Vite
TanStack Query
```

TypeScript strict mode should be enabled.

Network requests should live in service/API modules rather than being scattered throughout components.

Example feature areas:

```text
auth
study-blocks
availability
dashboard
booking
```

MSW should be used to mock backend behavior while frontend and backend development happen in parallel.

---

# Dashboard

The dashboard should answer:

- What study sessions are coming up?
- Which room is planned?
- Who is responsible for each reservation?
- Has the room been booked?
- Which shifts are unassigned?
- Which bookings require attention?

Example:

```text
Monday, October 12

6:00 PM - 10:00 PM
Room: 441D
Assigned: Ronald
Status: BOOKED

10:00 PM - 12:00 AM
Room: 441D
Assigned: Genaro
Status: ASSIGNED
```

---

# Availability

Members should be able to specify when they cannot take a reservation shift.

Support:

- One-time unavailable periods
- Recurring unavailable periods

The assignment service uses this information before assigning shifts.

---

# Phase 1 Booking Flow

```text
Create Study Block
        |
        v
Generate Shifts
        |
        v
Assign Members
        |
        v
Store Assignments
        |
        v
Wait for 14-Day Booking Window
        |
        v
Send Assigned Member Reminder
        |
        v
User Opens LibCal
        |
        v
User Completes Reservation
        |
        v
User Marks Shift BOOKED
        |
        v
Shared Dashboard Updates
```

---

# Deployment Plan

Target production infrastructure:

```text
Oracle Cloud VM
        |
        v
Docker Compose
        |
        +------------------+
        |                  |
        v                  v
      Caddy          Spring Boot
        |                  |
        v                  v
   React static        PostgreSQL
```

A dynamic DNS provider such as DuckDNS may be used for the project hostname.

Caddy will handle HTTPS.

---

# Database Backups

PostgreSQL should be backed up using scheduled:

```text
pg_dump
```

Backups should be:

- Compressed
- Rotated
- Stored separately from the live database when possible

Oracle Object Storage may be evaluated for backup storage.

The exact free-tier limits and retention strategy must be verified before production deployment.

---

# Secrets

Secrets must never be committed to Git.

Examples:

```text
Database passwords
SMTP credentials
Session secrets
Cloud credentials
API credentials
```

Production secrets should be stored in the VM environment/configuration.

CI/CD secrets should use GitHub Actions secrets.

The public repository must only contain fake development credentials/data.

---

# Docker

Local development should eventually be startable using Docker Compose where practical.

At minimum, PostgreSQL should be available through a development Compose configuration.

Production will also use containers.

Because the planned cloud VM may use ARM architecture, container image compatibility must be checked before deployment.

---

# CI/CD

GitHub Actions should run automated checks for pull requests.

Backend:

```text
Compile
Unit tests
Integration tests
```

Frontend:

```text
Install dependencies
Type check
Unit tests
Build
```

No pull request should be merged when required CI checks are failing.

---

# Collaboration Workflow

Ronald and Genaro should work through feature branches.

Example:

```text
main
 |
 +-- feature/study-blocks
 |
 +-- feature/dashboard
 |
 +-- feature/auth
```

Workflow:

```text
Create issue
    |
    v
Create feature branch
    |
    v
Implement
    |
    v
Test
    |
    v
Push branch
    |
    v
Open Pull Request
    |
    v
Other developer reviews
    |
    v
CI passes
    |
    v
Merge
```

Avoid both developers making unrelated changes directly on `main`.

---

# Repository Structure

Planned repository layout:

```text
StudyRoomBooker/
|
|-- backend/
|   |-- pom.xml
|   `-- src/
|
|-- frontend/
|   |-- package.json
|   `-- src/
|
|-- docs/
|   `-- adr/
|
|-- infra/
|
|-- docker-compose.yml
|
|-- ARCHITECTURE.md
|
|-- README.md
|
`-- .github/
    `-- workflows/
```

The structure should be created incrementally as the corresponding components are implemented.

Do not create empty architecture solely for appearance.

---

# Architecture Decision Records

Important architectural decisions should be recorded under:

```text
docs/adr/
```

Initial ADRs:

```text
0001 - Magic-link authentication and session cookies
0002 - PostgreSQL timestamptz, America/Chicago, injected Clock
0003 - Shift splitting on 15-minute boundaries with 4-hour maximum
0004 - Shift status represents booking state; notifications stored separately
0005 - Booking availability is 14 days in advance
0006 - TypeScript API types generated from OpenAPI
0007 - Single study group for v1
0008 - Docker Compose deployment
0009 - EmailSender abstraction
0010 - One active booking responsibility per member per local day
```

### ADR 0005 Note

Do **not** encode an assumption yet about whether LibCal releases availability at midnight or at the exact reservation time.

For now ADR 0005 establishes only:

```text
Reservations become available 14 days in advance.
```

The exact scheduling behavior can be refined after observing LibCal.

---

# Milestone -1 — Pre-Implementation Research

## Ronald

- Verify/observe the LibCal 14-day booking behavior.
- Capture the normal LibCal reservation URL.
- Capture the URL after navigating to/selecting a specific booking date/time/room.
- Compare the two URLs with ChatGPT to determine whether useful deep-link parameters exist.
- Begin investigating Oracle Cloud deployment.

## Genaro

- Create dedicated project Gmail account.
- Configure 2FA.
- Create/test an app password if required for SMTP.
- Verify email delivery to a UARK inbox.
- Configure DuckDNS if still selected for deployment.
- Contact the University Libraries regarding the proposed Phase 2 Chrome extension.

## Together

- Review ADRs 0001-0010.
- Confirm unresolved assumptions before implementation.
- Keep Phase 1 independent of Phase 2 approval.

---

# Milestone 0 — Foundation

Target responsibilities may be split as follows.

## Ronald

- Spring Boot project
- Java 21 configuration
- Maven
- Development/test profiles
- PostgreSQL development environment
- Backend package structure
- Clock/time configuration
- Initial Testcontainers setup

## Genaro

- React + TypeScript project
- Vite
- Routing
- API service structure
- TanStack Query
- MSW
- openapi-typescript
- Initial GitHub Actions frontend workflow

## Together

- Initial Flyway migration
- API contract
- OpenAPI setup
- Seed rooms
- Seed library hours
- Fake development members
- Fake authentication if needed temporarily
- Fake AssignmentService
- EmailSender interface

---

# Milestone 1 — Core Planning

## Backend

Implement:

- Member
- Room
- LibraryHours
- MemberUnavailability
- StudyBlock
- StudyBlockBackupRoom
- Shift
- Repositories
- Study-block CRUD
- Shift generation
- Assignment logic
- Release/reassignment logic
- Database constraints
- Tests

## Frontend

Implement:

- Study-block creation
- Study-block editing
- Availability management
- Initial dashboard
- Room selection
- Backup-room ordering

---

# Milestone 2 — Authentication & Notifications

Implement:

- Magic-link authentication
- One-time tokens
- Session security
- EmailSender production implementation
- Notification outbox
- Booking reminders
- Action tokens
- Booking confirmation
- Scheduler
- Retry behavior
- Dashboard integration

Test:

- Safe Links behavior
- Expired tokens
- Duplicate scheduler runs
- Missed scheduler execution
- DST/timezone cases
- Failed email retries

---

# Milestone 3 — Deployment

Together:

- Production Docker Compose
- Caddy
- DNS
- HTTPS
- Production environment variables
- PostgreSQL backup job
- Backup retention
- Health checks
- Logging
- Failed-notification visibility
- End-to-end testing
- Deployment documentation

Target:

**Phase 1 working before Thanksgiving/finals study period if development progresses as planned.**

---

# Scope Cut Line

If development falls behind, cut these first:

- Group alerts
- Extra nudges
- Weekly recurring study blocks
- Administrative views

Do **not** cut:

- Authentication
- Study-block creation
- Shift generation
- Shift assignment
- Dashboard
- At least one booking reminder
- Marking a shift BOOKED

Those features define the usable Phase 1 product.

---

# Phase 2 — Chrome Extension Assisted Booking

**Status: Pending Library Approval**

Phase 2 must not begin until the University Libraries' response is understood.

## Goal

Reduce the number of manual steps required to prepare a LibCal reservation without:

- Storing UARK credentials
- Bypassing authentication
- Bypassing MFA
- Performing unattended bookings
- Crawling LibCal
- Polling LibCal

Potential flow:

```text
StudyRoomBooker
      |
      v
User clicks Book Room
      |
      v
Short-lived booking token
      |
      v
Chrome Extension
      |
      v
Fetch booking details
      |
      v
Open LibCal
      |
      v
Prepare intended room/date/time
      |
      v
User completes authentication
      |
      v
User reviews
      |
      v
User accepts terms
      |
      v
User submits
```

The exact implementation depends on:

1. Library approval.
2. LibCal page behavior.
3. What the Phase 1 URL investigation discovers.

---

# Phase 2 Extension Architecture

Potential architecture:

```text
React Web App
      |
      v
chrome.runtime.sendMessage
      |
      v
Extension Service Worker
      |
      v
Short-Lived Booking Token
      |
      v
Spring Boot API
      |
      v
Booking Details
      |
      v
Content Script on LibCal
```

Extension state should use appropriate Chrome extension storage rather than exposing sensitive state in URLs.

Permissions should be kept as narrow as possible.

Selectors should be centralized so LibCal page changes can be repaired without rewriting the extension.

---

# Phase 2 Responsibilities

## Ronald

Potential responsibilities:

- Booking-details endpoint
- Short-lived single-use booking tokens
- Booking-result endpoint
- Backend tests
- Web-app Book Room handoff
- Extension service worker

## Genaro

Potential responsibilities:

- Extension manifest
- Content script
- LibCal selectors
- Room/date/time preparation
- Backup-room handling
- Popup/status UI
- Installation documentation
- Backend booking-source tracking

## Pair

- Permissions review
- Security review
- Integration testing
- LibCal page-change handling
- End-to-end testing
- Packaging/documentation

These responsibilities may change depending on the library's response.

---

# If Phase 2 Extension Is Rejected

The project continues with Enhanced Manual Booking.

StudyRoomBooker will still provide:

- Fair assignment
- Shift planning
- Booking reminders
- Room information
- Backup rooms
- Date/time information
- Best available LibCal link
- Booking status tracking
- Shared dashboard

An official LibCal API may be investigated if the University Libraries offer authorized access.

A rejected extension does **not** block completion of the project.

---

# Testing Strategy

## Unit Tests

Examples:

```text
Shift splitting
Assignment eligibility
Fairness calculations
Unavailability
Release/reassignment
Booking-window calculations
Notification creation
Token expiration
```

## Integration Tests

Use Testcontainers with PostgreSQL.

Test:

- Flyway migrations
- Repository behavior
- PostgreSQL constraints
- Transaction behavior
- Authentication persistence
- Notification outbox

## Frontend Tests

Use:

```text
Vitest
React Testing Library
MSW
```

Test:

- Study-block forms
- Dashboard states
- Authentication flows
- Booking confirmation
- Error states
- Loading states

---

# Development Principles

1. Build Phase 1 before Phase 2.
2. Keep the system as a modular monolith initially.
3. Put business rules in services.
4. Protect important invariants in PostgreSQL as well as Java.
5. Use migrations for every database change.
6. Keep frontend/backend contracts synchronized through OpenAPI.
7. Make time testable with an injected Clock.
8. Keep external integrations behind interfaces.
9. Do not store university credentials.
10. Do not automate around a library restriction.
11. Prefer official APIs when available.
12. Keep the repository runnable by both developers.
13. Require review before merging meaningful changes.
14. Document major architectural decisions.

---

# Known Limitations

Phase 1 does not directly control LibCal.

Therefore:

- BOOKED is based on user confirmation.
- Cancelling a shift in StudyRoomBooker does not automatically cancel LibCal.
- Room availability may change before the assigned member completes the booking.
- Deep-link capabilities are unknown until the URL investigation is completed.
- Phase 2 depends on library approval and LibCal behavior.

These are accepted limitations for the first version.

---

# Future Possibilities

After the core application is complete, possible improvements include:

- Official LibCal API integration
- Approved Chrome extension
- Better reservation verification
- Multiple study groups
- Mobile-friendly PWA behavior
- Calendar integration
- Push notifications
- SMS notifications
- Analytics
- Admin tools
- More sophisticated fairness algorithms

These should not delay Phase 1.

---

# Resume / Portfolio Goals

The project should demonstrate practical experience with:

```text
Java
Spring Boot
Spring Security
REST APIs
PostgreSQL
SQL constraints
Flyway
React
TypeScript
OpenAPI
Docker
CI/CD
Cloud deployment
Testing
Git collaboration
Pull requests
Code review
System design
Authentication
Scheduled jobs
Email integration
```

Both contributors should maintain a record of the features they personally designed and implemented so the project can be accurately discussed in interviews.

---

# Current Next Steps

Before application code is generated:

### Ronald

1. Investigate the LibCal 14-day booking behavior.
2. Copy the normal LibCal URL.
3. Navigate to/select a specific reservation date/time/room and copy that URL.
4. Give both URLs to ChatGPT for comparison.
5. Investigate the planned deployment environment.

### Genaro

1. Prepare the project email account.
2. Test SMTP/email delivery.
3. Investigate DNS setup.
4. Contact the University Libraries regarding the proposed Chrome extension.

### Together

1. Review this architecture document.
2. Review ADRs 0001-0010.
3. Resolve remaining Milestone -1 questions.
4. Only then begin Milestone 0 implementation.

---

# Project Rule

**Do not generate the application solely from this architecture document without first reviewing the current milestone and creating the appropriate issue/feature branch.**

The purpose of this document is to keep Ronald and Genaro aligned on the system design while the project is developed incrementally.

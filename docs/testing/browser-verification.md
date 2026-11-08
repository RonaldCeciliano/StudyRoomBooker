# Browser verification — November 5, 2026

Verified the development stack using installed Brave through temporary Playwright
tooling, with PostgreSQL, Spring Boot (`dev`), and Vite (`dev`, without MSW).

- Desktop viewport: 1440 × 1000; mobile viewport: 390 × 844.
- Dashboard displayed the three database fixtures with room, member, and booking
  status. No mock service worker was registered.
- With the browser timezone set to America/Los_Angeles, the dashboard correctly
  displayed the noon–3 PM fixture in America/Chicago.
- Desktop and mobile screenshots were visually inspected. Mobile had no
  horizontal overflow; times, members, and badges remained readable.
- A same-origin browser fetch to `POST /api/study-blocks` created a future
  noon–9 PM block with an ordered backup room and returned 201. After reload,
  the dashboard showed its three new unassigned 3-hour shifts from PostgreSQL.
- An invalid `{}` create request returned 400 application/problem+json. The
  browser reported the expected failed request; successful flows produced no
  console errors or uncaught JavaScript errors.
- Removed only the temporary block, its shifts, and backup relation afterward;
  the three original demo shifts remain.

Block creation was exercised through browser fetch because the planning form is
not implemented yet. Authentication and member assignment remain future work.
Screenshots and temporary automation tooling were kept outside the repository.

Automated checks for this implementation: 15 backend tests, 12 frontend tests,
frontend build/lint, OpenAPI validation, and generated-type freshness all passed.

## Member assignment follow-up — November 5, 2026

Verified the member-assignment implementation in Brave against the same real
development stack, without mocks. Created a new noon–9 PM study block on a date
without existing responsibilities, using the original fictional members.

- The POST returned 201 and generated three 3-hour shifts.
- After dashboard reload, the first shift was ASSIGNED to Ronald (Demo), the
  second to Genaro (Demo), and the third remained UNASSIGNED with no member.
- Original fixture assignments/bookings remained unchanged.
- Desktop (1440 × 1000) and mobile (390 × 844) displayed the expected names,
  times, and statuses. Mobile scrolling and the fixed navigation were inspected
  in separate viewport screenshots; there was no horizontal overflow.
- With the browser timezone set to America/Los_Angeles, times still displayed
  in America/Chicago. No mock workers or unexpected browser errors occurred.
- Removed only the temporary block, backup relation, and three generated shifts;
  the three original demo shifts remain.

Automated checks: 32 backend tests and 12 frontend tests pass, along with frontend
build/lint, OpenAPI validation, and generated-type freshness. Backend tests cover
eligibility, workload ranking, weekly/local-day boundaries, one-time and recurring
unavailability, DST, repeat assignment, concurrent creates, and transaction rollback.

## Release/reassignment follow-up — November 5, 2026

Verified in Brave against the real development backend and PostgreSQL, without
mocks. A POST to release the assigned demo shift returned 401 without a principal;
forged member-ID/email headers, query parameters, and JSON also returned 401. Both
responses used application/problem+json. GET returned 405. Comparing the full shift
list before/after confirmed no assignment or booking changes. The dashboard still
rendered on desktop/mobile with no horizontal overflow or uncaught JavaScript errors.
The expected failed HTTP requests appeared in the browser console.

A successful signed-in browser release cannot be exercised yet because there is
no sign-in flow or release UI. Successful owner release, replacement names in the
real HTTP shift list, rejection cases, and no-replacement behavior are verified
against PostgreSQL using a principal adapter defined only on the test classpath.
The production-style HTTP test confirms those test headers do not authenticate
ordinary requests. No development data was added or removed for this check.

Automated checks: 45 backend tests, 12 frontend tests, build/lint, OpenAPI validation,
and generated-type freshness pass. Release tests include immediate-only exclusion,
weekly fairness, untouched unrelated shifts and booking data, rollback after failed
reassignment, and simultaneous duplicate releases preserving the replacement owner.

## Magic-link sign-in — November 7, 2026

Verified with Spring Boot (`dev`) against a throwaway PostgreSQL 17 container, using curl
with a cookie jar (no browser automation):

- `GET /api/session` returned `authenticated: false` and set `XSRF-TOKEN`.
- Anonymous `GET /api/shifts` returned 401; `POST /ott/generate` without the CSRF header
  returned 403.
- `POST /ott/generate` returned 204 for both a dev member and an unknown email; only the
  member's link appeared in the `LoggingEmailSender` log.
- `GET /login/ott?token=…` did not sign in. `POST /login/ott` returned 204, after which
  `/api/session` showed the member and `GET /api/shifts` returned 200.
- Reusing the token returned 401 ProblemDetail; `POST /logout` returned 204 and
  `GET /api/shifts` returned 401 again.

The React sign-in, Continue, and sign-out screens are covered by MSW tests; they were
not exercised in a real browser for this change.

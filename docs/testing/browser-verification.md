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

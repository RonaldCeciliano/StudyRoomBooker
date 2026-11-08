import { http, HttpResponse } from 'msw'
import { shifts } from './data'
import { mockSession, VALID_MAGIC_TOKEN } from './session'

const problem = (status: number, title: string, detail: string) =>
  HttpResponse.json({ title, status, detail }, { status, headers: { 'Content-Type': 'application/problem+json' } })

const unauthorized = () => problem(401, 'Unauthorized', 'Sign in to continue.')

// Fake backend used by tests and by `npm run dev:mock`. In mock mode, sign in with
// any email, then open /login/magic?token=valid-demo-token.
export const handlers = [
  http.get('/api/session', () => HttpResponse.json(mockSession.current)),
  http.post('/ott/generate', () => new HttpResponse(null, { status: 204 })),
  http.post('/login/ott', async ({ request }) => {
    const form = new URLSearchParams(await request.text())
    if (form.get('token') !== VALID_MAGIC_TOKEN) {
      return problem(401, 'Unauthorized', 'This sign-in link is invalid, expired, or already used.')
    }
    mockSession.signIn()
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('/logout', () => {
    mockSession.signOut()
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('/api/shifts', () => (mockSession.current.authenticated ? HttpResponse.json(shifts) : unauthorized())),
  http.post('/api/shifts/:id/booked', async ({ params, request }) => {
    const shift = shifts.find((s) => s.id === Number(params.id))
    if (!shift) return problem(404, 'Not Found', 'Shift not found')
    if (!shift.assignedToMe) return problem(403, 'Forbidden', 'Only the assigned member can mark this shift booked')
    const body = (await request.json().catch(() => ({}))) as { bookedRoomId?: number }
    const room = body.bookedRoomId === undefined ? shift.bookingRooms[0] : shift.bookingRooms.find((r) => r.id === body.bookedRoomId)
    if (!room) return problem(400, 'Bad Request', 'Booked room must be the planned room or an active backup room')
    shift.status = 'BOOKED'
    shift.bookedRoomCode = room.code
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('/api/shifts/:id/release', ({ params }) => {
    const shift = shifts.find((s) => s.id === Number(params.id))
    if (!shift) return problem(404, 'Not Found', 'Shift not found')
    if (!shift.assignedToMe || shift.status !== 'ASSIGNED') {
      return problem(409, 'Conflict', 'Only future, unbooked assignments can be released')
    }
    Object.assign(shift, { status: 'UNASSIGNED', memberName: null, assignedToMe: false })
    return new HttpResponse(null, { status: 204 })
  }),
]

import { http, HttpResponse } from 'msw'
import { mockRooms, shifts, studyBlocks, unavailability } from './data'
import type { Unavailability, UnavailabilityInput } from '../features/availability/api'
import type { StudyBlock, StudyBlockInput } from '../features/study-blocks/api'
import { mockSession, VALID_MAGIC_TOKEN } from './session'

const withSeconds = (time: string) => (time.length === 5 ? `${time}:00` : time)

function toBlock(input: StudyBlockInput): Omit<StudyBlock, 'id'> {
  return {
    kind: 'ONE_TIME',
    date: input.date,
    startTime: withSeconds(input.startTime),
    endTime: withSeconds(input.endTime),
    preferredRoomId: input.preferredRoomId,
    backupRoomIds: input.backupRoomIds ?? [],
  }
}

// A small subset of the server's validation, enough to exercise error display.
function validateBlock(input: StudyBlockInput): string | undefined {
  if (input.endTime <= input.startTime) return 'endTime must be after startTime on the same date'
  if (!/^\d{2}:(00|15|30|45)/.test(input.startTime) || !/^\d{2}:(00|15|30|45)/.test(input.endTime)) {
    return 'Times must align to 15-minute boundaries'
  }
  if (input.backupRoomIds?.includes(input.preferredRoomId)) return 'Preferred room cannot also be a backup'
  return undefined
}

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
  http.get('/api/rooms', () => (mockSession.current.authenticated ? HttpResponse.json(mockRooms) : unauthorized())),
  http.get('/api/study-blocks', () =>
    mockSession.current.authenticated ? HttpResponse.json(studyBlocks) : unauthorized(),
  ),
  http.post('/api/study-blocks', async ({ request }) => {
    const input = (await request.json()) as StudyBlockInput
    const invalid = validateBlock(input)
    if (invalid) return problem(400, 'Bad Request', invalid)
    const block: StudyBlock = { ...toBlock(input), id: Math.max(0, ...studyBlocks.map((b) => b.id)) + 1 }
    studyBlocks.push(block)
    return HttpResponse.json(block, { status: 201 })
  }),
  http.put('/api/study-blocks/:id', async ({ params, request }) => {
    const index = studyBlocks.findIndex((b) => b.id === Number(params.id))
    if (index < 0) return problem(404, 'Not Found', 'One-time study block not found')
    const input = (await request.json()) as StudyBlockInput
    const invalid = validateBlock(input)
    if (invalid) return problem(400, 'Bad Request', invalid)
    studyBlocks[index] = { ...toBlock(input), id: studyBlocks[index].id }
    return HttpResponse.json(studyBlocks[index])
  }),
  http.get('/api/me/unavailability', () =>
    mockSession.current.authenticated ? HttpResponse.json(unavailability) : unauthorized(),
  ),
  http.post('/api/me/unavailability', async ({ request }) => {
    const input = (await request.json()) as UnavailabilityInput
    if (input.endTime <= input.startTime) return problem(400, 'Bad Request', 'endTime must be after startTime')
    if (input.kind === 'ONE_TIME' && !input.date) return problem(400, 'Bad Request', 'date is required for one-time unavailability')
    const entry: Unavailability = {
      id: Math.max(0, ...unavailability.map((u) => u.id)) + 1,
      kind: input.kind,
      date: input.kind === 'ONE_TIME' ? input.date ?? null : null,
      weekday: input.kind === 'WEEKLY' ? input.weekday ?? null : null,
      validFrom: input.kind === 'WEEKLY' ? input.validFrom ?? '2026-11-05' : null,
      validUntil: input.kind === 'WEEKLY' ? input.validUntil ?? null : null,
      startTime: input.startTime,
      endTime: input.endTime,
    }
    unavailability.push(entry)
    return HttpResponse.json(entry, { status: 201 })
  }),
  http.delete('/api/me/unavailability/:id', ({ params }) => {
    const index = unavailability.findIndex((u) => u.id === Number(params.id))
    if (index < 0) return problem(404, 'Not Found', 'Unavailability not found')
    unavailability.splice(index, 1)
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

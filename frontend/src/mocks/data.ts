import type { Shift } from '../features/shifts/api'

// Fake development data. Times are UTC instants; the UI shows them in America/Chicago.
const rooms = {
  d: { id: 1, code: '441D', name: 'Group Study 441D' },
  e: { id: 2, code: '441E', name: 'Group Study 441E' },
}

// The mock session is Genaro, so shift 2 is "mine".
export const initialShifts: Shift[] = [
  {
    id: 1,
    startsAt: '2026-10-12T23:00:00Z',
    endsAt: '2026-10-13T03:00:00Z',
    status: 'BOOKED',
    plannedRoomCode: '441D',
    memberName: 'Ronald',
    bookedRoomCode: '441D',
    assignedToMe: false,
    bookingRooms: [rooms.d, rooms.e],
  },
  {
    id: 2,
    startsAt: '2026-10-13T03:00:00Z',
    endsAt: '2026-10-13T05:00:00Z',
    status: 'ASSIGNED',
    plannedRoomCode: '441D',
    memberName: 'Genaro',
    bookedRoomCode: null,
    assignedToMe: true,
    bookingRooms: [rooms.d, rooms.e],
  },
  {
    id: 3,
    startsAt: '2026-10-14T19:00:00Z',
    endsAt: '2026-10-14T23:00:00Z',
    status: 'UNASSIGNED',
    plannedRoomCode: '441E',
    memberName: null,
    bookedRoomCode: null,
    assignedToMe: false,
    bookingRooms: [rooms.e],
  },
]

/** Mutable copy that mock write handlers change; reset between tests. */
export let shifts: Shift[] = structuredClone(initialShifts)

export function resetShifts() {
  shifts = structuredClone(initialShifts)
}

import type { Shift } from '../features/shifts/api'
import type { Room, StudyBlock } from '../features/study-blocks/api'

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
    libcalCancelRequired: false,
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
    libcalCancelRequired: false,
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
    libcalCancelRequired: false,
    bookingRooms: [rooms.e],
  },
]

export const mockRooms: Room[] = [rooms.d, rooms.e, { id: 3, code: '441C', name: 'Group Study 441C' }]

export const initialStudyBlocks: StudyBlock[] = [
  {
    id: 1,
    kind: 'ONE_TIME',
    date: '2026-11-10',
    startTime: '12:00:00',
    endTime: '21:00:00',
    preferredRoomId: 1,
    backupRoomIds: [2],
  },
]

/** Mutable copies that mock write handlers change; reset between tests. */
export let shifts: Shift[] = structuredClone(initialShifts)
export let studyBlocks: StudyBlock[] = structuredClone(initialStudyBlocks)

export function resetShifts() {
  shifts = structuredClone(initialShifts)
  studyBlocks = structuredClone(initialStudyBlocks)
}

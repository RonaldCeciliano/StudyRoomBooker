import type { Shift } from '../features/shifts/api'

// Fake development data. Times are UTC instants; the UI shows them in America/Chicago.
export const shifts: Shift[] = [
  {
    id: 1,
    startsAt: '2026-10-12T23:00:00Z',
    endsAt: '2026-10-13T03:00:00Z',
    status: 'BOOKED',
    plannedRoomCode: '441D',
    memberName: 'Ronald',
  },
  {
    id: 2,
    startsAt: '2026-10-13T03:00:00Z',
    endsAt: '2026-10-13T05:00:00Z',
    status: 'ASSIGNED',
    plannedRoomCode: '441D',
    memberName: 'Genaro',
  },
  {
    id: 3,
    startsAt: '2026-10-14T19:00:00Z',
    endsAt: '2026-10-14T23:00:00Z',
    status: 'UNASSIGNED',
    plannedRoomCode: '441E',
    memberName: null,
  },
]

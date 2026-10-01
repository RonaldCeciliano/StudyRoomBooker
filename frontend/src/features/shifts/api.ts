import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../api/client'

// Provisional shape until the API contract is agreed on with the backend.
// openapi-typescript will generate the real type from the OpenAPI spec.
export type ShiftStatus = 'UNASSIGNED' | 'ASSIGNED' | 'BOOKED' | 'MISSED' | 'CANCELLED'

export interface Shift {
  id: number
  /** ISO 8601 instant. */
  startsAt: string
  /** ISO 8601 instant. */
  endsAt: string
  status: ShiftStatus
  plannedRoomCode: string
  memberName: string | null
}

export const shiftKeys = {
  all: ['shifts'] as const,
}

export function fetchShifts(): Promise<Shift[]> {
  return apiFetch<Shift[]>('/api/shifts')
}

export function useShifts() {
  return useQuery({ queryKey: shiftKeys.all, queryFn: fetchShifts })
}

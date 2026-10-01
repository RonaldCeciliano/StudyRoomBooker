import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../api/client'
import type { components, paths } from '../../api/generated/schema'

export type Shift = components['schemas']['Shift']
export type ShiftStatus = Shift['status']
export type ShiftsResponse = paths['/api/shifts']['get']['responses'][200]['content']['application/json']

export const shiftKeys = {
  all: ['shifts'] as const,
}

export function fetchShifts(): Promise<ShiftsResponse> {
  return apiFetch<ShiftsResponse>('/api/shifts')
}

export function useShifts() {
  return useQuery({ queryKey: shiftKeys.all, queryFn: fetchShifts })
}

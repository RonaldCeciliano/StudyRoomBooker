import { useQuery } from '@tanstack/react-query'
import { apiFetch } from '../../api/client'
import type { Schemas } from '../../api/types'

export type ShiftStatus = Schemas['ShiftStatus']
export type Shift = Schemas['Shift']

export const shiftKeys = {
  all: ['shifts'] as const,
}

export function fetchShifts(): Promise<Shift[]> {
  return apiFetch<Shift[]>('/api/shifts')
}

export function useShifts() {
  return useQuery({ queryKey: shiftKeys.all, queryFn: fetchShifts })
}

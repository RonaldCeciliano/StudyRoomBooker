import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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

export type RoomOption = components['schemas']['RoomOption']

/** LibCal's general reservation page. There is no booking-specific deep link (ARCHITECTURE.md). */
export const LIBCAL_RESERVE_URL = 'https://uark.libcal.com/reserve'

export function useMarkBooked() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ shiftId, bookedRoomId }: { shiftId: number; bookedRoomId?: number }) =>
      apiFetch<void>(`/api/shifts/${shiftId}/booked`, {
        method: 'POST',
        body: JSON.stringify(bookedRoomId === undefined ? {} : { bookedRoomId }),
      }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: shiftKeys.all }),
  })
}

export function useReleaseShift() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (shiftId: number) => apiFetch<void>(`/api/shifts/${shiftId}/release`, { method: 'POST' }),
    onSettled: () => queryClient.invalidateQueries({ queryKey: shiftKeys.all }),
  })
}

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../api/client'
import type { components } from '../../api/generated/schema'

export type Unavailability = components['schemas']['Unavailability']
export type UnavailabilityInput = components['schemas']['CreateUnavailability']

export const availabilityKeys = { mine: ['unavailability', 'me'] as const }

export function useMyUnavailability() {
  return useQuery({ queryKey: availabilityKeys.mine, queryFn: () => apiFetch<Unavailability[]>('/api/me/unavailability') })
}

export function useAddUnavailability() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: UnavailabilityInput) =>
      apiFetch<Unavailability>('/api/me/unavailability', { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: availabilityKeys.mine }),
  })
}

export function useRemoveUnavailability() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiFetch<void>(`/api/me/unavailability/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: availabilityKeys.mine }),
  })
}

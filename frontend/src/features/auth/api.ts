import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch, apiPostForm } from '../../api/client'
import type { components } from '../../api/generated/schema'

export type Session = components['schemas']['Session']

export const sessionKeys = {
  current: ['session'] as const,
}

export function fetchSession(): Promise<Session> {
  return apiFetch<Session>('/api/session')
}

export function useSession() {
  return useQuery({ queryKey: sessionKeys.current, queryFn: fetchSession, staleTime: 60_000 })
}

/** Always resolves the same way for members and non-members; the server never says which. */
export function useRequestMagicLink() {
  return useMutation({
    mutationFn: (email: string) => apiPostForm<void>('/ott/generate', { username: email.trim() }),
  })
}

export function useSignIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (token: string) => apiPostForm<void>('/login/ott', { token }),
    // Clear everything cached while signed out, then load the new session.
    onSuccess: async () => {
      queryClient.removeQueries()
      await queryClient.fetchQuery({ queryKey: sessionKeys.current, queryFn: fetchSession })
    },
  })
}

export function useSignOut() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiFetch<void>('/logout', { method: 'POST' }),
    onSuccess: () => {
      queryClient.removeQueries()
      queryClient.setQueryData<Session>(sessionKeys.current, { authenticated: false, email: null, name: null })
    },
  })
}

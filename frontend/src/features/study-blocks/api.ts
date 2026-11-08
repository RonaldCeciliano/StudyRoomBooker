import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiFetch } from '../../api/client'
import type { components } from '../../api/generated/schema'
import { shiftKeys } from '../shifts/api'

export type StudyBlock = components['schemas']['StudyBlock']
export type StudyBlockInput = components['schemas']['CreateStudyBlock']
export type Room = components['schemas']['RoomOption']

export const studyBlockKeys = {
  all: ['study-blocks'] as const,
  rooms: ['rooms'] as const,
}

export function useStudyBlocks() {
  return useQuery({ queryKey: studyBlockKeys.all, queryFn: () => apiFetch<StudyBlock[]>('/api/study-blocks') })
}

export function useRooms() {
  return useQuery({ queryKey: studyBlockKeys.rooms, queryFn: () => apiFetch<Room[]>('/api/rooms'), staleTime: 5 * 60_000 })
}

/** Creates when `id` is undefined, otherwise edits. Shifts change too, so both lists refresh. */
export function useSaveStudyBlock() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id?: number; input: StudyBlockInput }) =>
      apiFetch<StudyBlock>(id === undefined ? '/api/study-blocks' : `/api/study-blocks/${id}`, {
        method: id === undefined ? 'POST' : 'PUT',
        body: JSON.stringify(input),
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: studyBlockKeys.all }),
        queryClient.invalidateQueries({ queryKey: shiftKeys.all }),
      ])
    },
  })
}

export function useDeleteStudyBlock() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => apiFetch<void>(`/api/study-blocks/${id}`, { method: 'DELETE' }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: studyBlockKeys.all }),
        queryClient.invalidateQueries({ queryKey: shiftKeys.all }),
      ])
    },
  })
}

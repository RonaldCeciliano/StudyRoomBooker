// Shared fetch wrapper for the Spring Boot API. The API is served from the
// same origin (Caddy in production, the Vite proxy in development), so the
// session cookie is sent automatically and no CORS setup is needed.
//
// Writes echo the XSRF-TOKEN cookie in the X-XSRF-TOKEN header (Spring Security's
// SPA CSRF pattern). GET /api/session sets the cookie, so it is fetched first if missing.

import type { components } from './generated/schema'

export type ProblemDetail = components['schemas']['ProblemDetail']

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | undefined

  constructor(status: number, problem: ProblemDetail | undefined) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

export function readCsrfToken(): string | undefined {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match ? decodeURIComponent(match[1]) : undefined
}

async function csrfToken(): Promise<string | undefined> {
  const existing = readCsrfToken()
  if (existing !== undefined) {
    return existing
  }
  await fetch('/api/session', { credentials: 'same-origin', headers: { Accept: 'application/json' } })
  return readCsrfToken()
}

export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (!SAFE_METHODS.has((init.method ?? 'GET').toUpperCase())) {
    const token = await csrfToken()
    if (token !== undefined) {
      headers.set('X-XSRF-TOKEN', token)
    }
  }

  const response = await fetch(path, { ...init, headers, credentials: 'same-origin' })

  if (!response.ok) {
    throw new ApiError(response.status, await readProblem(response))
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/** POSTs an application/x-www-form-urlencoded body, as Spring Security's login endpoints expect. */
export function apiPostForm<T>(path: string, fields: Record<string, string>): Promise<T> {
  return apiFetch<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(fields),
  })
}

async function readProblem(response: Response): Promise<ProblemDetail | undefined> {
  const contentType = response.headers.get('Content-Type') ?? ''
  if (!contentType.includes('json')) {
    return undefined
  }
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return undefined
  }
}

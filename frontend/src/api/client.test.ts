import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../mocks/node'
import { ApiError, apiFetch } from './client'

describe('apiFetch', () => {
  it('returns parsed JSON for successful responses', async () => {
    server.use(http.get('/api/example', () => HttpResponse.json({ ok: true })))

    await expect(apiFetch('/api/example')).resolves.toEqual({ ok: true })
  })

  it('returns undefined for 204 No Content', async () => {
    server.use(http.post('/api/example', () => new HttpResponse(null, { status: 204 })))

    await expect(apiFetch('/api/example', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('throws an ApiError carrying the ProblemDetail body', async () => {
    server.use(
      http.get('/api/example', () =>
        HttpResponse.json(
          { title: 'Not Found', status: 404, detail: 'Shift 7 does not exist' },
          { status: 404, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    const error = await apiFetch('/api/example').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 404, message: 'Shift 7 does not exist' })
  })

  it('throws an ApiError with a generic message for non-JSON errors', async () => {
    server.use(http.get('/api/example', () => new HttpResponse('Bad Gateway', { status: 502 })))

    await expect(apiFetch('/api/example')).rejects.toThrow('Request failed with status 502')
  })
})

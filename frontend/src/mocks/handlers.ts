import { http, HttpResponse } from 'msw'
import { shifts } from './data'
import { mockSession, VALID_MAGIC_TOKEN } from './session'

const problem = (status: number, title: string, detail: string) =>
  HttpResponse.json({ title, status, detail }, { status, headers: { 'Content-Type': 'application/problem+json' } })

const unauthorized = () => problem(401, 'Unauthorized', 'Sign in to continue.')

// Fake backend used by tests and by `npm run dev:mock`. In mock mode, sign in with
// any email, then open /login/magic?token=valid-demo-token.
export const handlers = [
  http.get('/api/session', () => HttpResponse.json(mockSession.current)),
  http.post('/ott/generate', () => new HttpResponse(null, { status: 204 })),
  http.post('/login/ott', async ({ request }) => {
    const form = new URLSearchParams(await request.text())
    if (form.get('token') !== VALID_MAGIC_TOKEN) {
      return problem(401, 'Unauthorized', 'This sign-in link is invalid, expired, or already used.')
    }
    mockSession.signIn()
    return new HttpResponse(null, { status: 204 })
  }),
  http.post('/logout', () => {
    mockSession.signOut()
    return new HttpResponse(null, { status: 204 })
  }),
  http.get('/api/shifts', () => (mockSession.current.authenticated ? HttpResponse.json(shifts) : unauthorized())),
]

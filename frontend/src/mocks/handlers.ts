import { http, HttpResponse } from 'msw'
import { shifts } from './data'

// Fake backend used by tests and by `npm run dev:mock` until the Spring Boot API exists.
export const handlers = [http.get('/api/shifts', () => HttpResponse.json(shifts))]

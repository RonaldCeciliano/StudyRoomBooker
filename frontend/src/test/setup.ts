import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from '../mocks/node'
import { resetShifts } from '../mocks/data'
import { actionTokenUsed } from '../mocks/handlers'
import { mockSession } from '../mocks/session'

// Fail tests that call an endpoint without a mock, instead of hitting the network.
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => {
  cleanup()
  server.resetHandlers()
  mockSession.signIn()
  resetShifts()
  actionTokenUsed.value = false
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})
afterAll(() => server.close())

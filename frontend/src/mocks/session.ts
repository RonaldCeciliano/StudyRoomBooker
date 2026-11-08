import type { Session } from '../features/auth/api'

// Mutable fake session for MSW. Tests start signed in; reset after each test.
export const VALID_MAGIC_TOKEN = 'valid-demo-token'

const signedIn: Session = { authenticated: true, email: 'genaro@example.test', name: 'Genaro (Demo)' }
const signedOut: Session = { authenticated: false, email: null, name: null }

export const mockSession = {
  current: { ...signedIn },
  signIn() {
    this.current = { ...signedIn }
  },
  signOut() {
    this.current = { ...signedOut }
  },
}

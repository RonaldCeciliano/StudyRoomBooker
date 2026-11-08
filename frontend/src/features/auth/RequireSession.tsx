import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useSession } from './api'

/** Renders children only for signed-in members; everyone else goes to the sign-in page. */
export function RequireSession({ children }: { children: ReactNode }) {
  const session = useSession()

  if (session.isPending) {
    return <p className="secondary-text">Loading…</p>
  }
  if (session.isError) {
    return (
      <p className="secondary-text" role="alert">
        Couldn’t check your sign-in. {session.error.message}
      </p>
    )
  }
  if (!session.data.authenticated) {
    return <Navigate to="/login" replace />
  }
  return children
}

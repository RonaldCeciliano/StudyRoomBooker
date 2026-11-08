import { Link, NavLink, Outlet, useNavigate } from 'react-router'
import { useSession, useSignOut } from '../features/auth/api'
import './Layout.css'

export function Layout() {
  const session = useSession()
  const signOut = useSignOut()
  const navigate = useNavigate()
  const signedIn = session.data?.authenticated === true

  return (
    <>
      <div className="scroll-edge" aria-hidden="true" />
      <header className="app-bar glass">
        <Link to="/" className="app-bar-brand">
          StudyRoomBooker
        </Link>
        <nav className="app-bar-nav" aria-label="Main">
          <NavLink to="/" end className="nav-pill">
            Dashboard
          </NavLink>
          {signedIn && (
            <button
              type="button"
              className="nav-pill nav-button"
              title={session.data?.email ?? undefined}
              disabled={signOut.isPending}
              onClick={() => signOut.mutate(undefined, { onSuccess: () => navigate('/login', { replace: true }) })}
            >
              Sign Out
            </button>
          )}
        </nav>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </>
  )
}

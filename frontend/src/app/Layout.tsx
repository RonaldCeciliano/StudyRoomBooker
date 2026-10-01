import { Link, NavLink, Outlet } from 'react-router'
import './Layout.css'

export function Layout() {
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
        </nav>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </>
  )
}

import { formatLibraryDate, formatLibraryTimeRange } from '../../lib/time'
import { type Shift, useShifts } from '../shifts/api'
import { ShiftStatusBadge } from '../shifts/ShiftStatusBadge'
import './dashboard.css'

export function DashboardPage() {
  return (
    <>
      <h1 className="page-title">Dashboard</h1>
      <section className="section" aria-labelledby="upcoming-sessions">
        <h2 id="upcoming-sessions" className="section-title">
          Upcoming Sessions
        </h2>
        <UpcomingShifts />
      </section>
    </>
  )
}

function UpcomingShifts() {
  const shifts = useShifts()

  if (shifts.isPending) {
    return <p className="secondary-text">Loading sessions…</p>
  }
  if (shifts.isError) {
    return (
      <p className="secondary-text" role="alert">
        Couldn’t load sessions. {shifts.error.message}
      </p>
    )
  }
  if (shifts.data.length === 0) {
    return (
      <p className="secondary-text">
        No study sessions yet. Planned sessions, rooms, and booking status will appear here.
      </p>
    )
  }
  return (
    <ul className="shift-list">
      {shifts.data.map((shift) => (
        <ShiftRow key={shift.id} shift={shift} />
      ))}
    </ul>
  )
}

function ShiftRow({ shift }: { shift: Shift }) {
  return (
    <li className="shift-row">
      <div>
        <p className="shift-date">{formatLibraryDate(shift.startsAt)}</p>
        <p className="secondary-text">
          {formatLibraryTimeRange(shift.startsAt, shift.endsAt)} · Room {shift.plannedRoomCode}
        </p>
      </div>
      <div className="shift-meta">
        <span className="secondary-text">{shift.memberName ?? 'No one assigned'}</span>
        <ShiftStatusBadge status={shift.status} />
      </div>
    </li>
  )
}

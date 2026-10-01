import type { ShiftStatus } from './api'
import './shifts.css'

const LABELS: Record<ShiftStatus, string> = {
  UNASSIGNED: 'Unassigned',
  ASSIGNED: 'Assigned',
  BOOKED: 'Booked',
  MISSED: 'Missed',
  CANCELLED: 'Cancelled',
}

export function ShiftStatusBadge({ status }: { status: ShiftStatus }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}>{LABELS[status]}</span>
}

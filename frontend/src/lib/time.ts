// Reservations follow library time (ADR 0002), so always display America/Chicago
// regardless of the viewer's own timezone.
const TIME_ZONE = 'America/Chicago'

const dateFormat = new Intl.DateTimeFormat('en-US', {
  timeZone: TIME_ZONE,
  weekday: 'long',
  month: 'long',
  day: 'numeric',
})

const timeFormat = new Intl.DateTimeFormat('en-US', {
  timeZone: TIME_ZONE,
  hour: 'numeric',
  minute: '2-digit',
})

export function formatLibraryDate(instant: string): string {
  return dateFormat.format(new Date(instant))
}

export function formatLibraryTimeRange(start: string, end: string): string {
  return `${timeFormat.format(new Date(start))} – ${timeFormat.format(new Date(end))}`
}

// Study blocks store library-local calendar dates and wall-clock times (no instant), so
// format them as-is in UTC to avoid shifting them by the viewer's or library's offset.
const localDateFormat = new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', weekday: 'long', month: 'long', day: 'numeric' })
const localTimeFormat = new Intl.DateTimeFormat('en-US', { timeZone: 'UTC', hour: 'numeric', minute: '2-digit' })

/** `2026-11-10` → `Tuesday, November 10`. */
export function formatLocalDate(date: string): string {
  const [year, month, day] = date.split('-').map(Number)
  return localDateFormat.format(new Date(Date.UTC(year, month - 1, day)))
}

/** `12:00:00` or `12:00` → `12:00 PM`. */
export function formatLocalTime(time: string): string {
  const [hours, minutes] = time.split(':').map(Number)
  return localTimeFormat.format(new Date(Date.UTC(1970, 0, 1, hours, minutes)))
}

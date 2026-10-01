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

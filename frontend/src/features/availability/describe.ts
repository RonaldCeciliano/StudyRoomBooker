import { formatLocalDate, formatLocalTime } from '../../lib/time'
import type { Unavailability } from './api'

export const WEEKDAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']

const endLabel = (time: string) => (time === '24:00' ? 'end of day' : formatLocalTime(time))

export function describeUnavailability(entry: Unavailability): string {
  const times = `${formatLocalTime(entry.startTime)} – ${endLabel(entry.endTime)}`
  if (entry.kind === 'ONE_TIME') {
    return `${formatLocalDate(entry.date!)} · ${times}`
  }
  const range = entry.validUntil
    ? `from ${formatLocalDate(entry.validFrom!)} until ${formatLocalDate(entry.validUntil)}`
    : `from ${formatLocalDate(entry.validFrom!)}`
  return `Every ${WEEKDAYS[entry.weekday! - 1]} · ${times} · ${range}`
}

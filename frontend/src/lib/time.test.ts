import { describe, expect, it } from 'vitest'
import { formatLibraryDate, formatLibraryTimeRange } from './time'

describe('library time formatting', () => {
  it('shows instants in America/Chicago during daylight time', () => {
    expect(formatLibraryDate('2026-10-12T23:00:00Z')).toBe('Monday, October 12')
    expect(formatLibraryTimeRange('2026-10-12T23:00:00Z', '2026-10-13T03:00:00Z')).toBe(
      '6:00 PM – 10:00 PM',
    )
  })

  it('shows instants in America/Chicago after daylight saving time ends', () => {
    // DST ends 2026-11-01, so 00:00 UTC on Nov 2 is 6:00 PM CST on Nov 1.
    expect(formatLibraryDate('2026-11-02T00:00:00Z')).toBe('Sunday, November 1')
    expect(formatLibraryTimeRange('2026-11-02T00:00:00Z', '2026-11-02T04:00:00Z')).toBe(
      '6:00 PM – 10:00 PM',
    )
  })

  it('uses the local date, not the UTC date, for late-evening shifts', () => {
    // 10:00 PM CDT on Oct 12 is already Oct 13 in UTC.
    expect(formatLibraryDate('2026-10-13T03:00:00Z')).toBe('Monday, October 12')
  })
})

describe('library-local study block values', () => {
  it('formats calendar dates and wall-clock times without time-zone shifts', async () => {
    const { formatLocalDate, formatLocalTime } = await import('./time')
    expect(formatLocalDate('2026-11-10')).toBe('Tuesday, November 10')
    expect(formatLocalTime('12:00:00')).toBe('12:00 PM')
    expect(formatLocalTime('00:15')).toBe('12:15 AM')
    expect(formatLocalTime('23:45:00')).toBe('11:45 PM')
  })
})

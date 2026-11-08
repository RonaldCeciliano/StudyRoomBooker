import { type FormEvent, useState } from 'react'
import '../auth/auth.css'
import '../shifts/shifts.css'
import '../study-blocks/planning.css'
import { useAddUnavailability, useMyUnavailability, useRemoveUnavailability } from './api'
import { describeUnavailability, WEEKDAYS } from './describe'

export function AvailabilityPage() {
  const entries = useMyUnavailability()
  const remove = useRemoveUnavailability()

  return (
    <>
      <h1 className="page-title">Availability</h1>
      <section className="section" aria-labelledby="unavailable-heading">
        <h2 id="unavailable-heading" className="section-title">
          When I can’t book
        </h2>
        <p className="secondary-text">
          New shifts won’t be assigned to you during these times (library time). Shifts already assigned to you
          don’t change; release them from the dashboard.
        </p>
        {entries.isPending ? (
          <p className="secondary-text">Loading…</p>
        ) : entries.isError ? (
          <p className="error-text" role="alert">
            Couldn’t load your availability. {entries.error.message}
          </p>
        ) : entries.data.length === 0 ? (
          <p className="secondary-text">No unavailable times. You can be assigned any shift.</p>
        ) : (
          <ul className="shift-list" aria-label="My unavailable times">
            {entries.data.map((entry) => (
              <li key={entry.id} className="shift-row">
                <span>{describeUnavailability(entry)}</span>
                <button
                  type="button"
                  className="button button-secondary"
                  disabled={remove.isPending}
                  aria-label={`Remove ${describeUnavailability(entry)}`}
                  onClick={() => remove.mutate(entry.id)}
                >
                  Remove
                </button>
              </li>
            ))}
          </ul>
        )}
        {remove.isError && (
          <p className="error-text" role="alert">
            Couldn’t remove it. {remove.error.message}
          </p>
        )}
      </section>
      <section className="section" aria-labelledby="add-unavailable-heading">
        <h2 id="add-unavailable-heading" className="section-title">
          Add unavailable time
        </h2>
        <AddUnavailabilityForm />
      </section>
    </>
  )
}

function AddUnavailabilityForm() {
  const add = useAddUnavailability()
  const [kind, setKind] = useState<'ONE_TIME' | 'WEEKLY'>('ONE_TIME')
  const [date, setDate] = useState('')
  const [weekday, setWeekday] = useState(1)
  const [validUntil, setValidUntil] = useState('')
  const [startTime, setStartTime] = useState('')
  const [endTime, setEndTime] = useState('')
  const [endOfDay, setEndOfDay] = useState(false)
  const [localError, setLocalError] = useState<string>()

  function submit(event: FormEvent) {
    event.preventDefault()
    const end = endOfDay ? '24:00' : endTime
    if (!end || end <= startTime) {
      setLocalError('End time must be after start time.')
      return
    }
    setLocalError(undefined)
    add.mutate(
      kind === 'ONE_TIME'
        ? { kind, date, startTime, endTime: end }
        : { kind, weekday, validUntil: validUntil || null, startTime, endTime: end },
      {
        onSuccess: () => {
          setDate('')
          setStartTime('')
          setEndTime('')
          setEndOfDay(false)
          setValidUntil('')
        },
      },
    )
  }

  const message = localError ?? add.error?.message

  return (
    <form className="block-form" aria-label="Add unavailable time" onSubmit={submit}>
      <fieldset className="backup-rooms">
        <legend className="field-label">Repeats</legend>
        <div className="form-actions">
          <label>
            <input type="radio" name="kind" checked={kind === 'ONE_TIME'} onChange={() => setKind('ONE_TIME')} /> One time
          </label>
          <label>
            <input type="radio" name="kind" checked={kind === 'WEEKLY'} onChange={() => setKind('WEEKLY')} /> Every week
          </label>
        </div>
      </fieldset>
      <div className="form-grid">
        {kind === 'ONE_TIME' ? (
          <label className="form-field">
            <span className="field-label">Date</span>
            <input className="text-field" type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
        ) : (
          <>
            <label className="form-field">
              <span className="field-label">Day</span>
              <select className="text-field" value={weekday} onChange={(e) => setWeekday(Number(e.target.value))}>
                {WEEKDAYS.map((day, index) => (
                  <option key={day} value={index + 1}>
                    {day}
                  </option>
                ))}
              </select>
            </label>
            <label className="form-field">
              <span className="field-label">Until (optional)</span>
              <input className="text-field" type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
            </label>
          </>
        )}
        <label className="form-field">
          <span className="field-label">From</span>
          <input className="text-field" type="time" required value={startTime} onChange={(e) => setStartTime(e.target.value)} />
        </label>
        <label className="form-field">
          <span className="field-label">To</span>
          <input
            className="text-field"
            type="time"
            required={!endOfDay}
            disabled={endOfDay}
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
          />
        </label>
      </div>
      <label>
        <input type="checkbox" checked={endOfDay} onChange={(e) => setEndOfDay(e.target.checked)} /> Until end of day
      </label>
      {message && (
        <p className="error-text" role="alert">
          {message}
        </p>
      )}
      <div className="form-actions">
        <button type="submit" className="button button-prominent" disabled={add.isPending}>
          {add.isPending ? 'Adding…' : 'Add'}
        </button>
      </div>
    </form>
  )
}

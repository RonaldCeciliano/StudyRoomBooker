import { type FormEvent, useState } from 'react'
import type { Room, StudyBlock, StudyBlockInput } from './api'

type Props = {
  rooms: Room[]
  initial?: StudyBlock
  saving: boolean
  error?: string
  onSave: (input: StudyBlockInput) => void
  onCancel: () => void
}

const hhmm = (time: string) => time.slice(0, 5)

/** Date and times are America/Chicago library time; the server validates hours and rooms. */
export function StudyBlockForm({ rooms, initial, saving, error, onSave, onCancel }: Props) {
  const [date, setDate] = useState(initial?.date ?? '')
  const [startTime, setStartTime] = useState(initial ? hhmm(initial.startTime) : '')
  const [endTime, setEndTime] = useState(initial ? hhmm(initial.endTime) : '')
  const [preferredRoomId, setPreferredRoomId] = useState<number | undefined>(initial?.preferredRoomId ?? rooms[0]?.id)
  const [backupRoomIds, setBackupRoomIds] = useState<number[]>(initial?.backupRoomIds ?? [])
  const [localError, setLocalError] = useState<string>()

  const roomLabel = (id: number) => {
    const room = rooms.find((r) => r.id === id)
    return room ? `${room.code} · ${room.name}` : `Room ${id}`
  }
  const backupChoices = rooms.filter((r) => r.id !== preferredRoomId && !backupRoomIds.includes(r.id))

  function submit(event: FormEvent) {
    event.preventDefault()
    if (endTime <= startTime) {
      setLocalError('End time must be after start time.')
      return
    }
    if (preferredRoomId === undefined) {
      setLocalError('Choose a preferred room.')
      return
    }
    setLocalError(undefined)
    onSave({ date, startTime, endTime, preferredRoomId, backupRoomIds: backupRoomIds.filter((id) => id !== preferredRoomId) })
  }

  const message = localError ?? error

  return (
    <form className="block-form" onSubmit={submit} aria-label={initial ? 'Edit study block' : 'New study block'}>
      <div className="form-grid">
        <label className="form-field">
          <span className="field-label">Date</span>
          <input className="text-field" type="date" required value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <label className="form-field">
          <span className="field-label">Start</span>
          <input className="text-field" type="time" step={900} required value={startTime} onChange={(e) => setStartTime(e.target.value)} />
        </label>
        <label className="form-field">
          <span className="field-label">End</span>
          <input className="text-field" type="time" step={900} required value={endTime} onChange={(e) => setEndTime(e.target.value)} />
        </label>
        <label className="form-field">
          <span className="field-label">Preferred room</span>
          <select className="text-field" required value={preferredRoomId ?? ''} onChange={(e) => setPreferredRoomId(Number(e.target.value))}>
            {rooms.map((room) => (
              <option key={room.id} value={room.id}>
                {room.code} · {room.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <fieldset className="backup-rooms">
        <legend className="field-label">Backup rooms, in order</legend>
        {backupRoomIds.length === 0 ? (
          <p className="secondary-text">No backup rooms.</p>
        ) : (
          <ol className="backup-list">
            {backupRoomIds.map((id, index) => (
              <li key={id}>
                <span>{roomLabel(id)}</span>
                <button
                  type="button"
                  className="button button-secondary button-small"
                  disabled={index === 0}
                  aria-label={`Move ${roomLabel(id)} up`}
                  onClick={() =>
                    setBackupRoomIds((ids) => {
                      const next = [...ids]
                      ;[next[index - 1], next[index]] = [next[index], next[index - 1]]
                      return next
                    })
                  }
                >
                  ↑
                </button>
                <button
                  type="button"
                  className="button button-secondary button-small"
                  aria-label={`Remove ${roomLabel(id)}`}
                  onClick={() => setBackupRoomIds((ids) => ids.filter((x) => x !== id))}
                >
                  Remove
                </button>
              </li>
            ))}
          </ol>
        )}
        {backupChoices.length > 0 && (
          <label className="form-field">
            <span className="visually-hidden">Add backup room</span>
            <select
              className="text-field"
              value=""
              onChange={(e) => e.target.value && setBackupRoomIds((ids) => [...ids, Number(e.target.value)])}
            >
              <option value="">Add backup room…</option>
              {backupChoices.map((room) => (
                <option key={room.id} value={room.id}>
                  {room.code} · {room.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </fieldset>

      {initial && (
        <p className="secondary-text">
          Changing the date or times regenerates this block’s shifts. Booked shifts are cancelled and must also be
          cancelled on LibCal.
        </p>
      )}
      {message && (
        <p className="error-text" role="alert">
          {message}
        </p>
      )}
      <div className="form-actions">
        <button type="submit" className="button button-prominent" disabled={saving}>
          {saving ? 'Saving…' : initial ? 'Save Changes' : 'Create Block'}
        </button>
        <button type="button" className="button button-secondary" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </div>
    </form>
  )
}

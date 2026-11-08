import { useState } from 'react'
import { LIBCAL_RESERVE_URL, type Shift, useMarkBooked, useReleaseShift } from './api'

/** Booking and release controls, shown only on the signed-in member's own assigned shifts. */
export function ShiftActions({ shift }: { shift: Shift }) {
  const markBooked = useMarkBooked()
  const release = useReleaseShift()
  const [roomId, setRoomId] = useState(shift.bookingRooms[0]?.id)
  const [confirmingRelease, setConfirmingRelease] = useState(false)
  const error = markBooked.error ?? release.error
  const busy = markBooked.isPending || release.isPending

  if (!shift.assignedToMe || shift.status !== 'ASSIGNED') {
    return null
  }

  return (
    <div className="shift-actions">
      <a className="button button-secondary" href={LIBCAL_RESERVE_URL} target="_blank" rel="noopener noreferrer">
        Book on LibCal
      </a>
      {shift.bookingRooms.length > 1 && (
        <label className="room-select">
          <span className="visually-hidden">Room you booked</span>
          <select value={roomId} onChange={(event) => setRoomId(Number(event.target.value))} disabled={busy}>
            {shift.bookingRooms.map((room) => (
              <option key={room.id} value={room.id}>
                Room {room.code}
              </option>
            ))}
          </select>
        </label>
      )}
      <button
        type="button"
        className="button button-prominent"
        disabled={busy}
        onClick={() => markBooked.mutate({ shiftId: shift.id, bookedRoomId: roomId })}
      >
        Mark Booked
      </button>
      {confirmingRelease ? (
        <>
          <button
            type="button"
            className="button button-destructive"
            disabled={busy}
            onClick={() => release.mutate(shift.id, { onSettled: () => setConfirmingRelease(false) })}
          >
            Confirm Release
          </button>
          <button type="button" className="button button-secondary" onClick={() => setConfirmingRelease(false)}>
            Keep
          </button>
        </>
      ) : (
        <button type="button" className="button button-secondary" disabled={busy} onClick={() => setConfirmingRelease(true)}>
          Release
        </button>
      )}
      {error && (
        <p className="shift-action-error" role="alert">
          {error.message}
        </p>
      )}
    </div>
  )
}

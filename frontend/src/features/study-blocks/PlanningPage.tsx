import { useState } from 'react'
import { formatLocalDate, formatLocalTime } from '../../lib/time'
import '../auth/auth.css'
import '../shifts/shifts.css'
import { type StudyBlock, useRooms, useSaveStudyBlock, useStudyBlocks } from './api'
import './planning.css'
import { StudyBlockForm } from './StudyBlockForm'

type Editing = { mode: 'closed' } | { mode: 'new' } | { mode: 'edit'; block: StudyBlock }

export function PlanningPage() {
  const blocks = useStudyBlocks()
  const rooms = useRooms()
  const save = useSaveStudyBlock()
  const [editing, setEditing] = useState<Editing>({ mode: 'closed' })

  const close = () => {
    save.reset()
    setEditing({ mode: 'closed' })
  }

  const roomCode = (id: number) => rooms.data?.find((r) => r.id === id)?.code ?? `#${id}`

  return (
    <>
      <h1 className="page-title">Planning</h1>
      <section className="section" aria-labelledby="study-blocks-heading">
        <div className="section-header">
          <h2 id="study-blocks-heading" className="section-title">
            Study Blocks
          </h2>
          {editing.mode === 'closed' && (
            <button type="button" className="button button-prominent" onClick={() => setEditing({ mode: 'new' })} disabled={!rooms.data}>
              New Block
            </button>
          )}
        </div>

        {editing.mode !== 'closed' &&
          (rooms.isError ? (
            <p className="error-text" role="alert">
              Couldn’t load rooms. {rooms.error.message}
            </p>
          ) : rooms.data ? (
            <StudyBlockForm
              key={editing.mode === 'edit' ? editing.block.id : 'new'}
              rooms={rooms.data}
              initial={editing.mode === 'edit' ? editing.block : undefined}
              saving={save.isPending}
              error={save.error?.message}
              onCancel={close}
              onSave={(input) =>
                save.mutate({ id: editing.mode === 'edit' ? editing.block.id : undefined, input }, { onSuccess: close })
              }
            />
          ) : (
            <p className="secondary-text">Loading rooms…</p>
          ))}

        {blocks.isPending ? (
          <p className="secondary-text">Loading study blocks…</p>
        ) : blocks.isError ? (
          <p className="secondary-text" role="alert">
            Couldn’t load study blocks. {blocks.error.message}
          </p>
        ) : blocks.data.length === 0 ? (
          <p className="secondary-text">No upcoming study blocks. Create one to plan reservation shifts.</p>
        ) : (
          <ul className="shift-list" aria-label="Upcoming study blocks">
            {blocks.data.map((block) => (
              <li key={block.id} className="shift-row">
                <div>
                  <p className="shift-date">{formatLocalDate(block.date)}</p>
                  <p className="secondary-text">
                    {formatLocalTime(block.startTime)} – {formatLocalTime(block.endTime)} · Room {roomCode(block.preferredRoomId)}
                    {block.backupRoomIds.length > 0 && ` · Backups ${block.backupRoomIds.map(roomCode).join(', ')}`}
                  </p>
                </div>
                <button
                  type="button"
                  className="button button-secondary"
                  aria-label={`Edit block on ${formatLocalDate(block.date)}`}
                  onClick={() => {
                    save.reset()
                    setEditing({ mode: 'edit', block })
                  }}
                >
                  Edit
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}


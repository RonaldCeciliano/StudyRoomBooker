import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { ApiError, apiFetch } from '../../api/client'
import type { components } from '../../api/generated/schema'
import { formatLibraryDate, formatLibraryTimeRange } from '../../lib/time'
import '../auth/auth.css'

type ActionPreview = components['schemas']['ActionPreview']

/**
 * Landing page for the reminder email's "I booked it" link. Works without signing in.
 * Opening it only previews the shift; pressing Confirm (a POST) uses the link.
 */
export function BookedActionPage() {
  const [params] = useSearchParams()
  const token = params.get('token') ?? ''
  const preview = useQuery({
    queryKey: ['action-preview', token],
    queryFn: () => apiFetch<ActionPreview>(`/api/actions/booked?token=${encodeURIComponent(token)}`),
    enabled: token !== '',
    retry: false,
  })
  const confirm = useMutation({
    mutationFn: () => apiFetch<void>('/api/actions/booked', { method: 'POST', body: JSON.stringify({ token }) }),
  })

  let content
  if (!token) {
    content = <InvalidLink />
  } else if (preview.isPending) {
    content = <p className="secondary-text">Loading…</p>
  } else if (preview.isError) {
    content = preview.error instanceof ApiError && preview.error.status === 404 ? <InvalidLink /> : (
      <p className="error-text" role="alert">
        Couldn’t load this link. {preview.error.message}
      </p>
    )
  } else if (confirm.isSuccess) {
    content = (
      <div role="status">
        <p>Marked booked. Thanks for reserving the room!</p>
        <p className="secondary-text">
          <Link to="/">Open the dashboard</Link> (sign-in required).
        </p>
      </div>
    )
  } else {
    const shift = preview.data
    content = (
      <div className="auth-form">
        <p>
          <strong>{formatLibraryDate(shift.startsAt)}</strong>
          <br />
          {formatLibraryTimeRange(shift.startsAt, shift.endsAt)} · Room {shift.plannedRoomCode}
        </p>
        {shift.status === 'BOOKED' ? (
          <p className="secondary-text">This shift is already marked booked.</p>
        ) : (
          <>
            <p className="secondary-text">
              Confirm that you reserved Room {shift.plannedRoomCode} on LibCal. Booked a backup room? Use the dashboard
              instead.
            </p>
            {confirm.isError && (
              <p className="error-text" role="alert">
                {confirm.error.message}
              </p>
            )}
            <button type="button" className="button button-prominent" onClick={() => confirm.mutate()} disabled={confirm.isPending}>
              {confirm.isPending ? 'Saving…' : 'Confirm Booked'}
            </button>
          </>
        )}
      </div>
    )
  }

  return (
    <>
      <h1 className="page-title">I Booked It</h1>
      <section className="section auth-card" aria-labelledby="booked-action-heading">
        <h2 id="booked-action-heading" className="section-title">
          Mark your shift booked
        </h2>
        {content}
      </section>
    </>
  )
}

function InvalidLink() {
  return (
    <p className="error-text" role="alert">
      This link is invalid, expired, or already used. You can still mark the shift booked from the{' '}
      <Link to="/">dashboard</Link>.
    </p>
  )
}

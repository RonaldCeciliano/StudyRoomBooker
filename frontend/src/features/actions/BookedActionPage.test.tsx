import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { shifts } from '../../mocks/data'
import { VALID_ACTION_TOKEN } from '../../mocks/handlers'
import { server } from '../../mocks/node'
import { mockSession } from '../../mocks/session'
import { renderApp } from '../../test/renderApp'

describe('BookedActionPage', () => {
  it('previews without using the link and confirms on POST, signed out', async () => {
    mockSession.signOut()
    const posts: string[] = []
    server.events.on('request:start', ({ request }) => {
      if (request.method === 'POST') posts.push(new URL(request.url).pathname)
    })
    const user = userEvent.setup()
    renderApp(`/actions/booked?token=${VALID_ACTION_TOKEN}`)

    expect(await screen.findByText('Monday, October 12')).toBeInTheDocument()
    expect(screen.getByText(/10:00 PM – 12:00 AM · Room 441D/)).toBeInTheDocument()
    expect(posts).toEqual([])

    await user.click(screen.getByRole('button', { name: 'Confirm Booked' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Marked booked.')
    expect(shifts[1].status).toBe('BOOKED')
    expect(posts).toEqual(['/api/actions/booked'])
    server.events.removeAllListeners()
  })

  it('explains invalid, used, or missing links', async () => {
    renderApp('/actions/booked?token=forged')
    expect(await screen.findByRole('alert')).toHaveTextContent('invalid, expired, or already used')
  })

  it('handles a link without a token', async () => {
    renderApp('/actions/booked')
    expect(await screen.findByRole('alert')).toHaveTextContent('invalid, expired, or already used')
  })
})

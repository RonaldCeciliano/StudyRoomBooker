import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../mocks/node'
import { mockSession, VALID_MAGIC_TOKEN } from '../../mocks/session'
import { renderApp } from '../../test/renderApp'

describe('magic-link sign-in', () => {
  it('sends signed-out visitors from the dashboard to the sign-in page', async () => {
    mockSession.signOut()
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Get a sign-in link' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign Out' })).not.toBeInTheDocument()
  })

  it('requests a link with the CSRF header and shows the same neutral message for any email', async () => {
    mockSession.signOut()
    document.cookie = 'XSRF-TOKEN=csrf-123'
    let sent: { username: string | null; csrf: string | null } | undefined
    server.use(
      http.post('/ott/generate', async ({ request }) => {
        sent = {
          username: new URLSearchParams(await request.text()).get('username'),
          csrf: request.headers.get('X-XSRF-TOKEN'),
        }
        return new HttpResponse(null, { status: 204 })
      }),
    )
    const user = userEvent.setup()
    renderApp('/login')

    await user.type(await screen.findByLabelText('Email'), '  stranger@example.test ')
    await user.click(screen.getByRole('button', { name: 'Email me a link' }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'If that email belongs to a group member, a sign-in link is on its way.',
    )
    expect(sent).toEqual({ username: 'stranger@example.test', csrf: 'csrf-123' })
  })

  it('does not use the token when the link is opened, only after Continue', async () => {
    mockSession.signOut()
    let attempts = 0
    server.events.on('request:start', ({ request }) => {
      if (new URL(request.url).pathname === '/login/ott') attempts++
    })
    const user = userEvent.setup()
    renderApp(`/login/magic?token=${VALID_MAGIC_TOKEN}`)

    const button = await screen.findByRole('button', { name: 'Continue' })
    expect(attempts).toBe(0)

    await user.click(button)

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(await screen.findAllByRole('listitem')).toHaveLength(3)
    expect(attempts).toBe(1)
    server.events.removeAllListeners()
  })

  it('explains expired or reused links', async () => {
    mockSession.signOut()
    const user = userEvent.setup()
    renderApp('/login/magic?token=already-used')

    await user.click(await screen.findByRole('button', { name: 'Continue' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('invalid, expired, or already used')
    expect(screen.getByRole('link', { name: 'Request a new link' })).toHaveAttribute('href', '/login')
  })

  it('handles links without a token', async () => {
    renderApp('/login/magic')

    expect(await screen.findByRole('alert')).toHaveTextContent('missing its sign-in token')
  })

  it('signs out and returns to the sign-in page', async () => {
    const user = userEvent.setup()
    renderApp('/')

    await user.click(await screen.findByRole('button', { name: 'Sign Out' }))

    expect(await screen.findByRole('heading', { name: 'Get a sign-in link' })).toBeInTheDocument()
    expect(mockSession.current.authenticated).toBe(false)
  })
})

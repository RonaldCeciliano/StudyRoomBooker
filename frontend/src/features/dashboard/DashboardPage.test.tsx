import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../mocks/node'
import { renderApp } from '../../test/renderApp'

describe('DashboardPage', () => {
  it('lists upcoming shifts in library time with room, member, and status', async () => {
    renderApp('/')

    const items = await screen.findAllByRole('listitem')
    expect(items).toHaveLength(3)

    const first = within(items[0])
    expect(first.getByText('Monday, October 12')).toBeInTheDocument()
    expect(first.getByText('6:00 PM – 10:00 PM · Room 441D')).toBeInTheDocument()
    expect(first.getByText('Ronald')).toBeInTheDocument()
    expect(first.getByText('Booked')).toBeInTheDocument()

    const third = within(items[2])
    expect(third.getByText('No one assigned')).toBeInTheDocument()
    expect(third.getByText('Unassigned')).toBeInTheDocument()
  })

  it('shows an empty state when there are no shifts', async () => {
    server.use(http.get('/api/shifts', () => HttpResponse.json([])))

    renderApp('/')

    expect(await screen.findByText(/No study sessions yet/)).toBeInTheDocument()
  })

  it('shows the ProblemDetail message when the request fails', async () => {
    server.use(
      http.get('/api/shifts', () =>
        HttpResponse.json(
          { title: 'Internal Server Error', status: 500, detail: 'Database unavailable' },
          { status: 500, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )

    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent('Database unavailable')
  })
})

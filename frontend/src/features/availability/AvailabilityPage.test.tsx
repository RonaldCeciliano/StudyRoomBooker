import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../mocks/node'
import { renderApp } from '../../test/renderApp'

describe('AvailabilityPage', () => {
  it('lists one-time and weekly unavailability in library time', async () => {
    renderApp('/availability')

    const list = await screen.findByRole('list', { name: 'My unavailable times' })
    const items = within(list).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Tuesday, November 10 · 9:00 AM – 12:30 PM')
    expect(items[1]).toHaveTextContent('Every Tuesday · 6:00 PM – end of day · from Thursday, November 5')
  })

  it('shows empty and error states', async () => {
    server.use(http.get('/api/me/unavailability', () => HttpResponse.json([])))
    renderApp('/availability')
    expect(await screen.findByText(/No unavailable times/)).toBeInTheDocument()
  })

  it('shows a load error', async () => {
    server.use(
      http.get('/api/me/unavailability', () =>
        HttpResponse.json({ status: 500, detail: 'Unable to load data. Please try again later.' }, {
          status: 500,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    )
    renderApp('/availability')
    expect(await screen.findByRole('alert')).toHaveTextContent('Unable to load data')
  })

  it('adds a one-time entry', async () => {
    let sent: unknown
    server.use(
      http.post('/api/me/unavailability', async ({ request }) => {
        sent = await request.clone().json()
        return undefined
      }),
    )
    const user = userEvent.setup()
    renderApp('/availability')

    const form = await screen.findByRole('form', { name: 'Add unavailable time' })
    await user.type(within(form).getByLabelText('Date'), '2026-11-12')
    await user.type(within(form).getByLabelText('From'), '14:00')
    await user.type(within(form).getByLabelText('To'), '16:00')
    await user.click(within(form).getByRole('button', { name: 'Add' }))

    expect(await screen.findByText('Thursday, November 12 · 2:00 PM – 4:00 PM')).toBeInTheDocument()
    expect(sent).toEqual({ kind: 'ONE_TIME', date: '2026-11-12', startTime: '14:00', endTime: '16:00' })
  })

  it('adds a weekly entry that runs until end of day', async () => {
    let sent: unknown
    server.use(
      http.post('/api/me/unavailability', async ({ request }) => {
        sent = await request.clone().json()
        return undefined
      }),
    )
    const user = userEvent.setup()
    renderApp('/availability')

    const form = await screen.findByRole('form', { name: 'Add unavailable time' })
    await user.click(within(form).getByLabelText('Every week'))
    await user.selectOptions(within(form).getByLabelText('Day'), '5')
    await user.type(within(form).getByLabelText('Until (optional)'), '2026-12-18')
    await user.type(within(form).getByLabelText('From'), '20:00')
    await user.click(within(form).getByLabelText('Until end of day'))
    await user.click(within(form).getByRole('button', { name: 'Add' }))

    expect(await screen.findByText(/Every Friday · 8:00 PM – end of day/)).toBeInTheDocument()
    expect(sent).toEqual({ kind: 'WEEKLY', weekday: 5, validUntil: '2026-12-18', startTime: '20:00', endTime: '24:00' })
  })

  it('validates times and shows server errors', async () => {
    const user = userEvent.setup()
    renderApp('/availability')

    const form = await screen.findByRole('form', { name: 'Add unavailable time' })
    await user.type(within(form).getByLabelText('Date'), '2026-11-12')
    await user.type(within(form).getByLabelText('From'), '16:00')
    await user.type(within(form).getByLabelText('To'), '14:00')
    await user.click(within(form).getByRole('button', { name: 'Add' }))
    expect(within(form).getByRole('alert')).toHaveTextContent('End time must be after start time.')

    server.use(
      http.post('/api/me/unavailability', () =>
        HttpResponse.json({ status: 400, detail: 'date must be today or later' }, {
          status: 400,
          headers: { 'Content-Type': 'application/problem+json' },
        }),
      ),
    )
    const to = within(form).getByLabelText('To')
    await user.clear(to)
    await user.type(to, '18:00')
    await user.click(within(form).getByRole('button', { name: 'Add' }))
    expect(await within(form).findByRole('alert')).toHaveTextContent('date must be today or later')
  })

  it('removes an entry', async () => {
    const user = userEvent.setup()
    renderApp('/availability')

    await user.click(await screen.findByRole('button', { name: /Remove Tuesday, November 10/ }))

    const list = await screen.findByRole('list', { name: 'My unavailable times' })
    await expect.poll(() => within(list).getAllByRole('listitem')).toHaveLength(1)
    expect(list).not.toHaveTextContent('November 10')
  })
})

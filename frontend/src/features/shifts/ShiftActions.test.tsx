import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { server } from '../../mocks/node'
import { renderApp } from '../../test/renderApp'

async function myShift() {
  const items = await screen.findAllByRole('listitem')
  return within(items[1])
}

describe('shift actions', () => {
  it('shows booking controls only on my own assigned shift, with the generic LibCal link', async () => {
    renderApp('/')

    const items = await screen.findAllByRole('listitem')
    expect(within(items[0]).queryByRole('button', { name: 'Mark Booked' })).not.toBeInTheDocument()
    expect(within(items[2]).queryByRole('button', { name: 'Mark Booked' })).not.toBeInTheDocument()

    const mine = within(items[1])
    expect(mine.getByText('Genaro (you)')).toBeInTheDocument()
    expect(mine.getByRole('link', { name: 'Book on LibCal' })).toHaveAttribute('href', 'https://uark.libcal.com/reserve')
    expect(mine.getByRole('combobox', { name: 'Room you booked' })).toHaveValue('1')
  })

  it('marks the shift booked in the chosen room and refreshes the list', async () => {
    let sent: unknown
    server.use(
      http.post('/api/shifts/:id/booked', async ({ request }) => {
        sent = await request.clone().json()
        return undefined
      }),
    )
    const user = userEvent.setup()
    renderApp('/')

    const mine = await myShift()
    await user.selectOptions(mine.getByRole('combobox', { name: 'Room you booked' }), '2')
    await user.click(mine.getByRole('button', { name: 'Mark Booked' }))

    expect(await mine.findByText('Booked')).toBeInTheDocument()
    expect(mine.getByText(/Room 441E/)).toBeInTheDocument()
    expect(mine.queryByRole('button', { name: 'Mark Booked' })).not.toBeInTheDocument()
    expect(sent).toEqual({ bookedRoomId: 2 })
  })

  it('releases only after confirming', async () => {
    const user = userEvent.setup()
    renderApp('/')

    const mine = await myShift()
    await user.click(mine.getByRole('button', { name: 'Release' }))
    await user.click(mine.getByRole('button', { name: 'Keep' }))
    expect(mine.getByText('Assigned')).toBeInTheDocument()

    await user.click(mine.getByRole('button', { name: 'Release' }))
    await user.click(mine.getByRole('button', { name: 'Confirm Release' }))

    expect(await mine.findByText('Unassigned')).toBeInTheDocument()
    expect(mine.getByText('No one assigned')).toBeInTheDocument()
  })

  it('shows the server error when marking booked fails', async () => {
    server.use(
      http.post('/api/shifts/:id/booked', () =>
        HttpResponse.json(
          { title: 'Conflict', status: 409, detail: 'Only your current or upcoming assigned shifts can be marked booked' },
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const user = userEvent.setup()
    renderApp('/')

    const mine = await myShift()
    await user.click(mine.getByRole('button', { name: 'Mark Booked' }))

    expect(await mine.findByRole('alert')).toHaveTextContent('Only your current or upcoming assigned shifts')
  })
})

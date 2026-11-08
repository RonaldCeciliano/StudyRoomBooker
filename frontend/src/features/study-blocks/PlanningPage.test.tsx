import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { studyBlocks } from '../../mocks/data'
import { server } from '../../mocks/node'
import { renderApp } from '../../test/renderApp'

describe('PlanningPage', () => {
  it('lists upcoming blocks with library-local times and rooms', async () => {
    renderApp('/planning')

    const list = await screen.findByRole('list', { name: 'Upcoming study blocks' })
    const block = within(list).getByRole('listitem')
    expect(block).toHaveTextContent('Tuesday, November 10')
    expect(block).toHaveTextContent('12:00 PM – 9:00 PM · Room 441D · Backups 441E')
  })

  it('shows an empty state', async () => {
    server.use(http.get('/api/study-blocks', () => HttpResponse.json([])))
    renderApp('/planning')

    expect(await screen.findByText(/No upcoming study blocks/)).toBeInTheDocument()
  })

  it('creates a block with ordered backup rooms', async () => {
    let sent: unknown
    server.use(
      http.post('/api/study-blocks', async ({ request }) => {
        sent = await request.clone().json()
        return undefined
      }),
    )
    const user = userEvent.setup()
    renderApp('/planning')

    await user.click(await screen.findByRole('button', { name: 'New Block' }))
    const form = screen.getByRole('form', { name: 'New study block' })
    await user.type(within(form).getByLabelText('Date'), '2026-11-12')
    await user.type(within(form).getByLabelText('Start'), '13:00')
    await user.type(within(form).getByLabelText('End'), '17:00')
    await user.selectOptions(within(form).getByLabelText('Preferred room'), '2')
    await user.selectOptions(within(form).getByLabelText('Add backup room'), '3')
    await user.selectOptions(within(form).getByLabelText('Add backup room'), '1')
    await user.click(within(form).getByRole('button', { name: 'Move 441D · Group Study 441D up' }))
    await user.click(within(form).getByRole('button', { name: 'Create Block' }))

    expect(await screen.findByText('Thursday, November 12')).toBeInTheDocument()
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
    expect(sent).toEqual({ date: '2026-11-12', startTime: '13:00', endTime: '17:00', preferredRoomId: 2, backupRoomIds: [1, 3] })
  })

  it('edits a block and warns that time changes cancel bookings', async () => {
    const user = userEvent.setup()
    renderApp('/planning')

    await user.click(await screen.findByRole('button', { name: 'Edit block on Tuesday, November 10' }))
    const form = screen.getByRole('form', { name: 'Edit study block' })
    expect(within(form).getByLabelText('Start')).toHaveValue('12:00')
    expect(form).toHaveTextContent('Booked shifts are cancelled and must also be cancelled on LibCal')

    const end = within(form).getByLabelText('End')
    await user.clear(end)
    await user.type(end, '18:00')
    await user.click(within(form).getByRole('button', { name: 'Save Changes' }))

    expect(await screen.findByText(/12:00 PM – 6:00 PM/)).toBeInTheDocument()
    expect(studyBlocks[0].endTime).toBe('18:00:00')
  })

  it('shows client and server validation errors without closing the form', async () => {
    const user = userEvent.setup()
    renderApp('/planning')

    await user.click(await screen.findByRole('button', { name: 'New Block' }))
    const form = screen.getByRole('form', { name: 'New study block' })
    await user.type(within(form).getByLabelText('Date'), '2026-11-12')
    await user.type(within(form).getByLabelText('Start'), '17:00')
    await user.type(within(form).getByLabelText('End'), '13:00')
    await user.click(within(form).getByRole('button', { name: 'Create Block' }))
    expect(within(form).getByRole('alert')).toHaveTextContent('End time must be after start time.')

    server.use(
      http.post('/api/study-blocks', () =>
        HttpResponse.json(
          { title: 'Bad Request', status: 400, detail: 'Study block must fit within library hours' },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const end = within(form).getByLabelText('End')
    await user.clear(end)
    await user.type(end, '23:00')
    await user.click(within(form).getByRole('button', { name: 'Create Block' }))

    expect(await within(form).findByRole('alert')).toHaveTextContent('Study block must fit within library hours')
    expect(screen.getByRole('form', { name: 'New study block' })).toBeInTheDocument()
  })

  it('is only reachable when signed in', async () => {
    const { mockSession } = await import('../../mocks/session')
    mockSession.signOut()
    renderApp('/planning')

    expect(await screen.findByRole('heading', { name: 'Get a sign-in link' })).toBeInTheDocument()
  })

  it('deletes a block only after confirming', async () => {
    const user = userEvent.setup()
    renderApp('/planning')

    await user.click(await screen.findByRole('button', { name: 'Delete block on Tuesday, November 10' }))
    expect(screen.getByText(/must also be cancelled on LibCal/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Keep' }))
    expect(studyBlocks).toHaveLength(1)

    await user.click(screen.getByRole('button', { name: 'Delete block on Tuesday, November 10' }))
    await user.click(screen.getByRole('button', { name: 'Confirm Delete' }))

    expect(await screen.findByText(/No upcoming study blocks/)).toBeInTheDocument()
    expect(studyBlocks).toHaveLength(0)
  })
})

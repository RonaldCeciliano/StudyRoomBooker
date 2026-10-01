import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp } from '../test/renderApp'

describe('routing', () => {
  it('shows the not-found page inside the layout for unknown paths', async () => {
    renderApp('/does-not-exist')

    expect(screen.getByRole('heading', { name: 'Page Not Found' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument()
  })

  it('navigates back to the dashboard from the not-found page', async () => {
    const user = userEvent.setup()
    renderApp('/does-not-exist')

    await user.click(screen.getByRole('link', { name: 'Back to Dashboard' }))

    expect(screen.getByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
  })
})

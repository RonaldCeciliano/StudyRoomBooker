import { createBrowserRouter, type RouteObject } from 'react-router'
import { BookedActionPage } from '../features/actions/BookedActionPage'
import { AvailabilityPage } from '../features/availability/AvailabilityPage'
import { LoginPage } from '../features/auth/LoginPage'
import { MagicLinkPage } from '../features/auth/MagicLinkPage'
import { RequireSession } from '../features/auth/RequireSession'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { PlanningPage } from '../features/study-blocks/PlanningPage'
import { ErrorPage } from './ErrorPage'
import { Layout } from './Layout'
import { NotFoundPage } from './NotFoundPage'

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      {
        // Errors render inside the layout so the navigation bar stays available.
        errorElement: <ErrorPage />,
        children: [
          {
            index: true,
            element: (
              <RequireSession>
                <DashboardPage />
              </RequireSession>
            ),
          },
          {
            path: 'planning',
            element: (
              <RequireSession>
                <PlanningPage />
              </RequireSession>
            ),
          },
          {
            path: 'availability',
            element: (
              <RequireSession>
                <AvailabilityPage />
              </RequireSession>
            ),
          },
          { path: 'actions/booked', element: <BookedActionPage /> },
          { path: 'login', element: <LoginPage /> },
          { path: 'login/magic', element: <MagicLinkPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)

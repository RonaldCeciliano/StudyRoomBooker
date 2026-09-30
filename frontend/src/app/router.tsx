import { createBrowserRouter } from 'react-router'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { ErrorPage } from './ErrorPage'
import { Layout } from './Layout'
import { NotFoundPage } from './NotFoundPage'

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      {
        // Errors render inside the layout so the navigation bar stays available.
        errorElement: <ErrorPage />,
        children: [
          { index: true, element: <DashboardPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])

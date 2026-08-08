import { createBrowserRouter } from 'react-router-dom'

import BasicLayout from '@/layouts/BasicLayout'
import HomePage from '@/pages/HomePage'
import LoginPage from '@/pages/LoginPage'

export const router = createBrowserRouter([
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <BasicLayout />,
    children: [
      {
        index: true,
        element: <HomePage />,
      },
    ],
  },
])

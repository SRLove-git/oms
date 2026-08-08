import type { ReactNode } from 'react'
import { createBrowserRouter } from 'react-router-dom'

import BasicLayout from '@/layouts/BasicLayout'
import HomePage from '@/pages/HomePage'
import LoginPage from '@/pages/LoginPage'
import OrdersPage from '@/pages/OrdersPage'
import ProductsPage from '@/pages/ProductsPage'
import AfterSalesPage from '@/pages/AfterSalesPage'

function RequireAuth({ children }: { children: ReactNode }) {
  const token = localStorage.getItem('oms-token')
  if (!token) {
    window.location.href = '/login'
    return null
  }
  return children
}

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
        element: (
          <RequireAuth>
            <HomePage />
          </RequireAuth>
        ),
      },
      {
        path: 'products',
        element: (
          <RequireAuth>
            <ProductsPage />
          </RequireAuth>
        ),
      },
      {
        path: 'orders',
        element: (
          <RequireAuth>
            <OrdersPage />
          </RequireAuth>
        ),
      },
      {
        path: 'after-sales',
        element: (
          <RequireAuth>
            <AfterSalesPage />
          </RequireAuth>
        ),
      },
    ],
  },
])

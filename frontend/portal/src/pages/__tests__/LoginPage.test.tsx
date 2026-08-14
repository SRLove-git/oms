import { Message } from '@arco-design/web-react'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { I18nextProvider } from 'react-i18next'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { login } from '@/api/auth'
import i18n from '@/i18n'
import LoginPage from '@/pages/LoginPage'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
}))

const mockedLogin = vi.mocked(login)

beforeEach(() => {
  mockedLogin.mockReset()
  mockedLogin.mockResolvedValue({
    token: 'test-token',
    user: {
      id: 1,
      username: 'merchant',
      realName: 'Merchant',
      userType: 1,
      merchantId: 1,
      status: 1,
    },
  })
  // Arco's Message uses react-dom's legacy render API, which is removed in
  // React 19; replace it with a no-op so the submit flow is side-effect free.
  vi.spyOn(Message, 'success').mockImplementation(() => () => {})
  localStorage.clear()
})

afterEach(() => {
  vi.restoreAllMocks()
  cleanup()
})

function renderLogin() {
  return render(
    <I18nextProvider i18n={i18n}>
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>
    </I18nextProvider>,
  )
}

describe('LoginPage', () => {
  it('renders the login form with localized texts', () => {
    renderLogin()
    expect(screen.getByText('商家门户登录')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('请输入账号')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('请输入密码')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /登\s*录/ })).toBeInTheDocument()
  })

  it('submits the form and calls the login api', async () => {
    const user = userEvent.setup()
    renderLogin()

    await user.click(screen.getByRole('button', { name: /登\s*录/ }))

    await waitFor(() => {
      expect(mockedLogin).toHaveBeenCalledWith('merchant', 'merchant123')
    })
  })
})

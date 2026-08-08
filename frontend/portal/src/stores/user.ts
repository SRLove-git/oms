import type { UserInfo } from '@/api/types'

const TOKEN_KEY = 'oms-token'
const USER_KEY = 'oms-user'

function getUser(): UserInfo | null {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) ?? 'null')
  } catch {
    return null
  }
}

export const userStore = {
  token: localStorage.getItem(TOKEN_KEY) ?? '',
  user: getUser(),

  init() {
    this.token = localStorage.getItem(TOKEN_KEY) ?? ''
    this.user = getUser()
  },

  save(token: string, user: UserInfo) {
    this.token = token
    this.user = user
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(USER_KEY, JSON.stringify(user))
  },

  logout() {
    this.token = ''
    this.user = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  },
}

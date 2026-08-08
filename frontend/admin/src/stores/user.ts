import { defineStore } from 'pinia'

import { fetchMe, login as loginApi } from '@/api/auth'
import type { UserInfo } from '@/api/types'

const TOKEN_KEY = 'oms-token'
const USER_KEY = 'oms-user'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) ?? '',
    user: JSON.parse(localStorage.getItem(USER_KEY) ?? 'null') as UserInfo | null,
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token),
    isAdmin: (state) => state.user?.userType === 1,
  },
  actions: {
    async login(username: string, password: string) {
      const result = await loginApi(username, password)
      this.token = result.token
      this.user = result.user
      localStorage.setItem(TOKEN_KEY, result.token)
      localStorage.setItem(USER_KEY, JSON.stringify(result.user))
    },
    async refreshMe() {
      this.user = await fetchMe()
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    },
  },
})

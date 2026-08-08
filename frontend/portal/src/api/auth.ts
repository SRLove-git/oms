import request from './request'
import type { ApiResult, UserInfo } from './types'

export interface LoginResponse {
  token: string
  user: UserInfo
}

export async function login(username: string, password: string) {
  const res = await request.post<ApiResult<LoginResponse>>('/auth/login', { username, password })
  return res.data.data
}

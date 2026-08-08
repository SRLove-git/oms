import request from './request'
import type { ApiResult, PageResult } from './types'

export interface UserRecord {
  id: number
  username: string
  realName?: string
  phone?: string
  email?: string
  userType: number
  merchantId?: number
  status: number
  createdAt?: string
}

export async function pageUsers(params: { keyword?: string; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<UserRecord>>>('/users', { params })
  return res.data.data
}

export async function createUser(data: {
  username: string
  password: string
  realName?: string
  phone?: string
  email?: string
  userType: number
  merchantId?: number
}) {
  const res = await request.post<ApiResult<number>>('/users', data)
  return res.data.data
}

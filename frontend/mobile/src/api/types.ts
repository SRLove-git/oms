export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  total: number
  records: T[]
}

export interface UserInfo {
  id: number
  username: string
  realName?: string
  userType: number
  merchantId?: number
  status: number
}

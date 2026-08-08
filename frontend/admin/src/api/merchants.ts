import request from './request'
import type { ApiResult, PageResult } from './types'

export interface MerchantRecord {
  id: number
  merchantNo: string
  name: string
  contactName?: string
  contactPhone?: string
  status: number
  createdAt?: string
}

export async function pageMerchants(params: {
  keyword?: string
  status?: number
  page: number
  size: number
}) {
  const res = await request.get<ApiResult<PageResult<MerchantRecord>>>('/merchants', { params })
  return res.data.data
}

export async function reviewMerchant(id: number, approved: boolean, reason?: string) {
  const res = await request.post<ApiResult<void>>(`/merchants/${id}/review`, { approved, reason })
  return res.data
}

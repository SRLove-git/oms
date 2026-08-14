import request from './request'
import type { ApiResult, PageResult } from './types'

export interface SkuRecord {
  id: number
  spuId: number
  spuNo?: string
  skuNo: string
  name: string
  spec?: string
  barcode?: string
  udi?: string
  registrationNo?: string
  price: string
  status: number
}

export async function pageSkus(params: { keyword?: string; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<SkuRecord>>>('/skus', { params })
  return res.data.data
}

export async function getSku(id: number | string) {
  const res = await request.get<ApiResult<SkuRecord>>(`/skus/${id}`)
  return res.data.data
}

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

export async function createSku(data: {
  spuNo: string
  spuName: string
  skuNo: string
  name: string
  spec?: string
  price: number
  udi?: string
  registrationNo?: string
}) {
  const res = await request.post<ApiResult<number>>('/skus', data)
  return res.data.data
}

export async function updateSkuStatus(id: number, status: number) {
  const res = await request.put<ApiResult<void>>(`/skus/${id}/status`, { status })
  return res.data
}

export async function deleteSku(id: number) {
  const res = await request.delete<ApiResult<void>>(`/skus/${id}`)
  return res.data
}

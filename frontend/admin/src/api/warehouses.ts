import request from './request'
import type { ApiResult } from './types'

export interface WarehouseRecord {
  id: number
  code: string
  name: string
  address?: string
  status: number
}

export async function listWarehouses() {
  const res = await request.get<ApiResult<WarehouseRecord[]>>('/warehouses')
  return res.data.data
}

export async function createWarehouse(data: { code: string; name: string; address?: string }) {
  const res = await request.post<ApiResult<number>>('/warehouses', data)
  return res.data.data
}

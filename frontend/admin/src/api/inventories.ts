import request from './request'
import type { ApiResult, PageResult } from './types'

export interface InventoryRecord {
  id: number
  warehouseId: number
  skuId: number
  skuNo?: string
  batchNo?: string
  quantity: number
  reservedQuantity: number
  frozenQuantity: number
  expireAt?: string
}

export interface InventoryTransactionRecord {
  id: number
  skuId: number
  batchNo?: string
  bizType: number
  bizNo: string
  changeQuantity: number
  beforeQuantity: number
  afterQuantity: number
  remark?: string
  createdAt?: string
}

export async function pageInventories(params: {
  warehouseId?: number
  skuId?: number
  page: number
  size: number
}) {
  const res = await request.get<ApiResult<PageResult<InventoryRecord>>>('/inventories', { params })
  return res.data.data
}

export async function inbound(data: {
  warehouseId: number
  skuId: number
  quantity: number
  batchNo?: string
  expireAt?: string
}) {
  const res = await request.post<ApiResult<void>>('/inventories/inbound', data)
  return res.data
}

export async function pageTransactions(params: { skuId?: number; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<InventoryTransactionRecord>>>(
    '/inventory-transactions',
    { params },
  )
  return res.data.data
}

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

export interface InventoryImportResult {
  totalRows: number
  successRows: number
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

export async function downloadInventoryImportTemplate() {
  const res = await request.get<Blob>('/inventories/import-template', { responseType: 'blob' })
  const url = URL.createObjectURL(res.data)
  const link = document.createElement('a')
  link.href = url
  link.download = '库存入库导入模板.xlsx'
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

export async function importInventory(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  const res = await request.post<ApiResult<InventoryImportResult>>('/inventories/import', formData, {
    timeout: 60_000,
  })
  return res.data.data
}

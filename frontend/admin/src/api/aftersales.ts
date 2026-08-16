import request from './request'
import type { ApiResult, PageResult } from './types'

export interface ReturnItemRecord {
  id: number
  orderItemId: number
  skuId: number
  quantity: number
  unitAmount: string
}

export interface RefundRecord {
  refundNo: string
  paymentNo: string
  amount: string
  method: number
  status: number
  channelTxnNo?: string
  refundedAt?: string
}

export interface RepairRecord {
  id: number
  repairNo: string
  returnNo: string
  skuId: number
  status: number
  faultDesc?: string
  repairFee: string
  assignedTo?: string
  finishedAt?: string
  logs: Array<{ action: string; content: string; operatorName?: string; createdAt: string }>
}

export interface ReturnOrder {
  id: number
  returnNo: string
  orderNo: string
  type: number
  status: number
  reason?: string
  totalAmount: string
  createdAt: string
  items: ReturnItemRecord[]
  refunds: RefundRecord[]
  repairs: RepairRecord[]
}

export interface ReturnOrderSummary {
  id: number
  returnNo: string
  orderNo: string
  type: number
  status: number
  reason?: string
  totalAmount: string
  createdAt: string
}

export async function pageReturnOrders(params: { status?: number; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<ReturnOrderSummary>>>('/return-orders', {
    params,
  })
  return res.data.data
}

export async function applyReturnOrder(data: {
  orderNo: string
  type: number
  reason: string
  items: Array<{ orderItemId: number; skuId: number; quantity: number }>
}) {
  const res = await request.post<ApiResult<ReturnOrder>>('/return-orders', data)
  return res.data.data
}

export async function getReturnOrder(returnNo: string) {
  const res = await request.get<ApiResult<ReturnOrder>>(`/return-orders/${returnNo}`)
  return res.data.data
}

export async function reviewReturnOrder(returnNo: string, approved: boolean, reason?: string) {
  const res = await request.post<ApiResult<void>>(`/return-orders/${returnNo}/review`, {
    approved,
    reason,
  })
  return res.data
}

export async function receiveReturnOrder(returnNo: string, qualified: boolean, remark?: string) {
  const res = await request.post<ApiResult<void>>(`/return-orders/${returnNo}/receive`, {
    qualified,
    remark,
  })
  return res.data
}

export async function refundReturnOrder(returnNo: string, paymentNo: string, amount: string) {
  const res = await request.post<ApiResult<void>>(`/return-orders/${returnNo}/refund`, {
    paymentNo,
    amount,
    method: 1,
  })
  return res.data
}

export async function exchangeShip(returnNo: string) {
  const res = await request.post<ApiResult<void>>(`/return-orders/${returnNo}/exchange-ship`)
  return res.data
}

export async function cancelReturnOrder(returnNo: string) {
  const res = await request.post<ApiResult<void>>(`/return-orders/${returnNo}/cancel`)
  return res.data
}

export async function createRepair(
  returnNo: string,
  payload: { skuId: number; faultDesc: string; assignedTo: string },
) {
  const res = await request.post<ApiResult<RepairRecord>>(`/return-orders/${returnNo}/repairs`, payload)
  return res.data.data
}

export async function repairProgress(
  repairId: number,
  payload: { action: string; content: string },
) {
  const res = await request.post<ApiResult<void>>(`/return-orders/repairs/${repairId}/progress`, payload)
  return res.data
}

import request from './request'
import type { ApiResult, PageResult } from './types'

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

export interface ReturnOrderDetail extends ReturnOrderSummary {
  items: Array<{
    id: number
    orderItemId: number
    skuId: number
    quantity: number
    unitAmount: string
  }>
  refunds: Array<{ refundNo: string; amount: string; status: number }>
  repairs: Array<{ repairNo: string; status: number; faultDesc?: string; repairFee: string }>
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
  const res = await request.post<ApiResult<ReturnOrderDetail>>('/return-orders', data)
  return res.data.data
}

export async function cancelReturnOrder(returnNo: string) {
  const res = await request.post<ApiResult<void>>(`/return-orders/${returnNo}/cancel`)
  return res.data
}

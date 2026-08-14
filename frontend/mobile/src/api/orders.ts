import request from './request'
import type { ApiResult, PageResult } from './types'

export interface OrderSummary {
  id: number
  orderNo: string
  merchantId: number
  orderType: number
  status: number
  totalAmount: string
  payAmount: string
  createdAt?: string
  itemCount: number
}

export interface OrderItemRecord {
  id: number
  skuId: number
  skuName: string
  quantity: number
  unitPrice: string
  totalPrice: string
}

export interface OrderLogRecord {
  fromStatus?: number
  toStatus: number
  operatorName?: string
  remark?: string
  createdAt?: string
}

export interface OrderDetail extends OrderSummary {
  currency: string
  remark?: string
  paidAt?: string
  timeoutAt?: string
  items: OrderItemRecord[]
  logs: OrderLogRecord[]
}

export async function createOrder(data: {
  merchantId?: number
  orderType: number
  remark?: string
  items: Array<{ skuId: number; quantity: number }>
}) {
  const res = await request.post<ApiResult<OrderDetail>>('/orders', data)
  return res.data.data
}

export async function pageOrders(params: { status?: number; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<OrderSummary>>>('/orders', { params })
  return res.data.data
}

export async function getOrder(orderNo: string) {
  const res = await request.get<ApiResult<OrderDetail>>(`/orders/${orderNo}`)
  return res.data.data
}

export async function payOrder(orderNo: string) {
  const res = await request.post<
    ApiResult<{ paymentNo: string; channel: string; payUrl: string; amount: string }>
  >(`/orders/${orderNo}/pay`, { channel: 'mock' })
  return res.data.data
}

export async function callbackMock(data: {
  paymentNo: string
  channelTxnNo: string
  amount: string
  status: string
}) {
  const res = await request.post<ApiResult<void>>('/payment-callbacks/mock', data)
  return res.data
}

export async function cancelOrder(orderNo: string, reason?: string) {
  const res = await request.post<ApiResult<void>>(`/orders/${orderNo}/cancel`, { reason })
  return res.data
}

export async function signOrder(orderNo: string) {
  const res = await request.post<ApiResult<void>>(`/orders/${orderNo}/sign`)
  return res.data
}

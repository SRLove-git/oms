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
  consignee?: string
  phone?: string
  address?: string
  paidAt?: string
  timeoutAt?: string
  items: OrderItemRecord[]
  logs: OrderLogRecord[]
}

export async function pageOrders(params: { status?: number; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<OrderSummary>>>('/orders', { params })
  return res.data.data
}

export async function getOrder(orderNo: string) {
  const res = await request.get<ApiResult<OrderDetail>>(`/orders/${orderNo}`)
  return res.data.data
}

export async function auditOrder(orderNo: string) {
  const res = await request.post<ApiResult<void>>(`/orders/${orderNo}/audit`)
  return res.data
}

export async function shipOrder(orderNo: string, trackingNo: string, carrier: string) {
  const res = await request.post<ApiResult<void>>(`/orders/${orderNo}/ship`, {
    trackingNo,
    carrier,
  })
  return res.data
}

export interface JtShipmentInput {
  reference_number: string
  service_code?: string
  consignee_details: {
    contact_name: string
    phone_number: string
    address: string
    postcode: string
  }
  item_details: Array<{
    weight: number
    weight_unit: 'G' | 'Kg'
    description?: string
    quantity?: number
  }>
}

export interface JtShipmentResult {
  tracking_id: string
  reference_number: string
  sorting_code?: string
}

export interface JtConnoteResult {
  reference_number: string
  connote_label_url: string
}

export async function createJtShipment(input: JtShipmentInput) {
  const res = await request.post<ApiResult<JtShipmentResult>>(
    '/logistics/jtexpress/shipments',
    input,
    // 极兔建单是同步外部调用，给足超时时间，避免前端先超时而后端已建单。
    { timeout: 35000 },
  )
  return res.data.data
}

export async function getJtConnote(trackingId: string, style: 'a4' | 'a6' = 'a6') {
  const res = await request.get<ApiResult<JtConnoteResult>>(
    `/logistics/jtexpress/connote/${encodeURIComponent(trackingId)}`,
    { params: { style } },
  )
  return res.data.data
}

export async function cancelJtShipment(trackingId: string, reason: string) {
  const res = await request.post<ApiResult<unknown>>('/logistics/jtexpress/cancel', {
    id: trackingId,
    reason,
  })
  return res.data
}

export async function signOrder(orderNo: string) {
  const res = await request.post<ApiResult<void>>(`/orders/${orderNo}/sign`)
  return res.data
}

export async function completeOrder(orderNo: string) {
  const res = await request.post<ApiResult<void>>(`/orders/${orderNo}/complete`)
  return res.data
}

export async function cancelOrder(orderNo: string, reason?: string) {
  const res = await request.post<ApiResult<void>>(`/orders/${orderNo}/cancel`, { reason })
  return res.data
}

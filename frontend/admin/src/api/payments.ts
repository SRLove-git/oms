import request from './request'
import type { ApiResult, PageResult } from './types'

export interface PaymentRecord {
  id: number
  paymentNo: string
  orderNo: string
  channel: string
  amount: string
  currency: string
  status: number
  channelTxnNo?: string
  createdAt?: string
  paidAt?: string
}

export async function pagePayments(params: { status?: number; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<PaymentRecord>>>('/payments', { params })
  return res.data.data
}

export async function refundPayment(paymentNo: string, amount: string) {
  const res = await request.post<ApiResult<void>>(`/payments/${paymentNo}/refund`, {
    amount,
    method: 1,
  })
  return res.data
}

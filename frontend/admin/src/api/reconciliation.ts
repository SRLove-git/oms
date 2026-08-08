import request from './request'
import type { ApiResult, PageResult } from './types'

export interface DiffItem {
  paymentNo: string
  orderNo: string
  channelAmount: string
  localAmount: string
  type: string
}

export interface ReconciliationRecord {
  id: number
  bizDate: string
  channel: string
  channelAmount: string
  localAmount: string
  diffCount: number
  status: number
  diffs: DiffItem[]
  handledAt?: string
  createdAt: string
}

export async function runReconciliation(payload: {
  bizDate: string
  channel: string
  simulateDiff: boolean
}) {
  const res = await request.post<ApiResult<ReconciliationRecord>>('/reconciliation/run', payload)
  return res.data.data
}

export async function pageReconciliation(params: {
  bizDate?: string
  channel?: string
  status?: number
  page: number
  size: number
}) {
  const res = await request.get<ApiResult<PageResult<ReconciliationRecord>>>('/reconciliation', {
    params,
  })
  return res.data.data
}

export async function handleReconciliation(id: number) {
  const res = await request.post<ApiResult<void>>(`/reconciliation/${id}/handle`)
  return res.data
}

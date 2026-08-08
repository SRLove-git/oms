import request from './request'
import type { ApiResult, PageResult } from './types'

export interface AuditLogRecord {
  id: number
  operatorId?: number
  operatorName?: string
  module: string
  action: string
  bizId?: string
  beforeData?: string
  afterData?: string
  createdAt?: string
}

export async function pageAuditLogs(params: { page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<AuditLogRecord>>>('/audit-logs', { params })
  return res.data.data
}

import request from './request'
import type { ApiResult, PageResult } from './types'

export interface LogisticsRecord {
  id: number
  orderNo: string
  trackingNo: string
  carrier: string
  status: string
  trace: string[]
  updatedAt: string
}

export interface MessageRecord {
  id: number
  messageNo: string
  channel: string
  scene?: string
  receiver: string
  title?: string
  content: string
  status: number
  retryCount: number
  errorMessage?: string
  sentAt?: string
  createdAt: string
}

export interface TemplateRecord {
  id: number
  code: string
  name: string
  channel: string
  scene: string
  titleTemplate?: string
  contentTemplate: string
  status: number
  updatedAt: string
}

export async function getLogisticsByOrder(orderNo: string) {
  const res = await request.get<ApiResult<LogisticsRecord>>(`/logistics/by-order/${orderNo}`)
  return res.data.data
}

export async function pageMessages(params: { status?: number; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<MessageRecord>>>('/notifications/messages', {
    params,
  })
  return res.data.data
}

export async function retryMessage(id: number) {
  const res = await request.post<ApiResult<MessageRecord>>(`/notifications/messages/${id}/retry`)
  return res.data.data
}

export async function saveTemplate(payload: {
  code: string
  name: string
  channel: string
  scene: string
  titleTemplate: string
  contentTemplate: string
}) {
  const res = await request.post<ApiResult<TemplateRecord>>('/notifications/templates', payload)
  return res.data.data
}

export async function pageTemplates(params: { scene?: string; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<TemplateRecord>>>('/notifications/templates', {
    params,
  })
  return res.data.data
}

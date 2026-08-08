import request from './request'
import type { ApiResult, PageResult } from './types'

export interface QualificationRecord {
  id: number
  merchantId: number
  qualificationNo: string
  qualificationType: number
  expireAt: string
  fileUrl?: string
  status: number
  createdAt?: string
}

export async function pageQualifications(params: { status?: number; page: number; size: number }) {
  const res = await request.get<ApiResult<PageResult<QualificationRecord>>>('/qualifications', {
    params,
  })
  return res.data.data
}

export async function reviewQualification(id: number, approved: boolean, reason?: string) {
  const res = await request.post<ApiResult<void>>(`/qualifications/${id}/review`, {
    approved,
    reason,
  })
  return res.data
}

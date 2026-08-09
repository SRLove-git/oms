import request from './request'
import type { ApiResult } from './types'

export interface SalesSummary {
  orderCount: number
  paidOrderCount: number
  paidAmount: string
  avgOrderValue: string
  grossProfit: string
  refundAmount: string
  totalCustomers: number
  repeatCustomers: number
  repurchaseRate: string
}

export interface SalesTrendItem {
  bizDate: string
  paidOrderCount: number
  paidAmount: string
}

export interface OrderSourceItem {
  orderType: number
  orderCount: number
  paidAmount: string
}

export interface DailySalesSnapshot {
  bizDate: string
  orderCount: number
  paidOrderCount: number
  paidAmount: string
  grossProfit: string
  refundAmount: string
}

export interface WarehouseStock {
  warehouseId: number
  warehouseName: string
  skuCount: number
  totalQuantity: number
  reservedQuantity: number
  frozenQuantity: number
}

export interface StockSummary {
  totalQuantity: number
  reservedQuantity: number
  frozenQuantity: number
  skuCount: number
  warehouseCount: number
}

export interface ExpiryBucket {
  bucket: string
  skuCount: number
  quantity: number
}

export interface TurnoverItem {
  skuId: number
  skuNo: string
  skuName: string
  outboundQuantity: number
  currentStock: number
  turnoverRate: string
}

export interface SlowMovingItem {
  skuId: number
  skuNo: string
  skuName: string
  currentStock: number
  lastSaleAt: string | null
}

export interface ChannelStats {
  channel: string
  totalCount: number
  successCount: number
  successAmount: string
  failCount: number
  refundCount: number
  refundAmount: string
  refundRate: string
}

export interface ReconciliationStats {
  channel: string
  status: number
  recordCount: number
  channelAmount: string
  localAmount: string
  diffCount: number
}

export interface TypeStats {
  type: number
  count: number
  totalAmount: string
  completedCount: number
  refundedAmount: string
}

export interface ReasonDistribution {
  reason: string
  count: number
}

export interface RepairDuration {
  repairCount: number
  avgMinutes: number
  minMinutes: number
  maxMinutes: number
}

export interface ReturnRate {
  returnCount: number
  completedOrderCount: number
  rate: string
}

export interface DateRange {
  startDate?: string
  endDate?: string
}

function range(): DateRange {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 29)
  return {
    startDate: start.toISOString().slice(0, 10),
    endDate: end.toISOString().slice(0, 10),
  }
}

export async function getSalesSummary(params: DateRange = range()) {
  const res = await request.get<ApiResult<SalesSummary>>('/reports/sales/summary', { params })
  return res.data.data
}

export async function getSalesTrend(params: DateRange = range()) {
  const res = await request.get<ApiResult<SalesTrendItem[]>>('/reports/sales/trend', { params })
  return res.data.data
}

export async function getOrderSource(params: DateRange = range()) {
  const res = await request.get<ApiResult<OrderSourceItem[]>>('/reports/sales/source', { params })
  return res.data.data
}

export async function getDailySales(params: DateRange = range()) {
  const res = await request.get<ApiResult<DailySalesSnapshot[]>>('/reports/sales/daily', { params })
  return res.data.data
}

export async function getWarehouseStock() {
  const res = await request.get<ApiResult<WarehouseStock[]>>('/reports/inventory/warehouse-stock')
  return res.data.data
}

export async function getStockSummary() {
  const res = await request.get<ApiResult<StockSummary>>('/reports/inventory/stock-summary')
  return res.data.data
}

export async function getExpiryDistribution() {
  const res = await request.get<ApiResult<ExpiryBucket[]>>('/reports/inventory/expiry-distribution')
  return res.data.data
}

export async function getTurnover(params: DateRange & { topN?: number } = { ...range(), topN: 10 }) {
  const res = await request.get<ApiResult<TurnoverItem[]>>('/reports/inventory/turnover', { params })
  return res.data.data
}

export async function getSlowMoving(params: { days?: number; limit?: number } = { days: 90, limit: 50 }) {
  const res = await request.get<ApiResult<SlowMovingItem[]>>('/reports/inventory/slow-moving', { params })
  return res.data.data
}

export async function getChannelStats(params: DateRange = range()) {
  const res = await request.get<ApiResult<ChannelStats[]>>('/reports/payments/channel-stats', { params })
  return res.data.data
}

export async function getReconciliationStats(params: DateRange = range()) {
  const res = await request.get<ApiResult<ReconciliationStats[]>>('/reports/payments/reconciliation-stats', {
    params,
  })
  return res.data.data
}

export async function getAfterSalesTypeStats(params: DateRange = range()) {
  const res = await request.get<ApiResult<TypeStats[]>>('/reports/aftersales/type-stats', { params })
  return res.data.data
}

export async function getReasonDistribution(params: DateRange & { topN?: number } = { ...range(), topN: 10 }) {
  const res = await request.get<ApiResult<ReasonDistribution[]>>('/reports/aftersales/reason-distribution', {
    params,
  })
  return res.data.data
}

export async function getRepairDuration(params: DateRange = range()) {
  const res = await request.get<ApiResult<RepairDuration>>('/reports/aftersales/repair-duration', { params })
  return res.data.data
}

export async function getReturnRate(params: DateRange = range()) {
  const res = await request.get<ApiResult<ReturnRate>>('/reports/aftersales/return-rate', { params })
  return res.data.data
}

export async function exportCsv(url: string, params: Record<string, unknown> = {}) {
  const res = await request.get<Blob>(url, { params, responseType: 'blob' })
  const disposition = String(res.headers['content-disposition'] ?? '')
  const match = disposition.match(/filename="?([^";]+)"?/)
  const filename = match ? match[1] : 'export.csv'
  const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = filename
  link.click()
  URL.revokeObjectURL(link.href)
}

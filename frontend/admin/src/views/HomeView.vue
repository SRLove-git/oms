<script setup lang="ts">
import { onMounted, ref } from 'vue'

import {
  getAfterSalesTypeStats,
  getChannelStats,
  getExpiryDistribution,
  getReturnRate,
  getSalesSummary,
  getSalesTrend,
  getStockSummary,
  type SalesSummary,
} from '@/api/reports'
import SimpleBar from '@/components/SimpleBar.vue'

const loading = ref(false)
const summary = ref<SalesSummary | null>(null)
const trend = ref<{ label: string; value: number }[]>([])
const expiry = ref<{ label: string; value: number }[]>([])
const channels = ref<{ label: string; value: number }[]>([])
const aftersales = ref<{ label: string; value: number }[]>([])
const stockSummary = ref({ totalQuantity: 0, skuCount: 0 })
const returnRate = ref({ rate: '0' })

const CHANNEL_NAMES: Record<string, string> = {
  wechat: '微信',
  alipay: '支付宝',
  visa: 'Visa',
  mastercard: 'Mastercard',
  balance: '余额',
  mock: 'Mock 渠道',
}

const TYPE_NAMES: Record<number, string> = {
  1: '退货',
  2: '换货',
  3: '维修',
}

async function load() {
  loading.value = true
  try {
    const end = new Date()
    const start = new Date()
    start.setDate(start.getDate() - 29)
    const params = {
      startDate: start.toISOString().slice(0, 10),
      endDate: end.toISOString().slice(0, 10),
    }
    const [s, t, e, c, a, stock, rate] = await Promise.all([
      getSalesSummary(params),
      getSalesTrend(params),
      getExpiryDistribution(),
      getChannelStats(params),
      getAfterSalesTypeStats(params),
      getStockSummary(),
      getReturnRate(params),
    ])
    summary.value = s
    trend.value = t.map((item) => ({
      label: item.bizDate.slice(5),
      value: Number(item.paidAmount),
    }))
    expiry.value = e.map((item) => ({ label: item.bucket, value: item.quantity }))
    channels.value = c.map((item) => ({
      label: CHANNEL_NAMES[item.channel] ?? item.channel,
      value: Number(item.successAmount),
    }))
    aftersales.value = a.map((item) => ({
      label: TYPE_NAMES[item.type] ?? String(item.type),
      value: item.count,
    }))
    stockSummary.value = stock
    returnRate.value = rate
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="home">
    <a-card :bordered="false" class="welcome-card">
      <template #title>OMS 数据大盘</template>
      P0/P1 全流程已闭环，阶段三报表中心与数据大盘已上线；以下数据为近 30 天口径（可到报表中心查看明细与导出）。
    </a-card>

    <a-row :gutter="[16, 16]" class="stat-row">
      <a-col
        v-for="stat in [
          { label: '支付金额(元)', value: Number(summary?.paidAmount ?? 0) },
          { label: '订单数', value: summary?.orderCount ?? 0 },
          { label: '客单价(元)', value: Number(summary?.avgOrderValue ?? 0) },
          { label: '复购率(%)', value: Number(summary?.repurchaseRate ?? 0) },
          { label: '总库存', value: stockSummary.totalQuantity },
          { label: '库存 SKU 数', value: stockSummary.skuCount },
          { label: '退款金额(元)', value: Number(summary?.refundAmount ?? 0) },
          { label: '退货率(%)', value: Number(returnRate.rate) },
        ]" :key="stat.label" :xs="24" :sm="12" :lg="6"
      >
        <a-card :bordered="false">
          <a-statistic :title="stat.label" :value="stat.value" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" title="销售趋势" :loading="loading">
          <SimpleBar :items="trend" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" title="库存效期分布" :loading="loading">
          <SimpleBar :items="expiry" color="rgb(var(--orange-6))" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" title="支付渠道成功金额" :loading="loading">
          <SimpleBar :items="channels" color="rgb(var(--purple-6))" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" title="售后类型单量" :loading="loading">
          <SimpleBar :items="aftersales" color="rgb(var(--red-6))" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
}
.stat-row {
  margin-bottom: 16px;
}
</style>

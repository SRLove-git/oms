<script setup lang="ts">
import { onMounted, ref } from 'vue'

import {
  exportCsv,
  getDailySales,
  getOrderSource,
  getSalesSummary,
  getSalesTrend,
  type DailySalesSnapshot,
  type OrderSourceItem,
  type SalesSummary,
  type SalesTrendItem,
} from '@/api/reports'
import SimpleBar from '@/components/SimpleBar.vue'

const loading = ref(false)
const startDate = ref('')
const endDate = ref('')

const summary = ref<SalesSummary | null>(null)
const trend = ref<SalesTrendItem[]>([])
const source = ref<OrderSourceItem[]>([])
const daily = ref<DailySalesSnapshot[]>([])

function defaultRange() {
  const end = new Date()
  const start = new Date()
  start.setDate(start.getDate() - 29)
  startDate.value = start.toISOString().slice(0, 10)
  endDate.value = end.toISOString().slice(0, 10)
}

async function load() {
  loading.value = true
  try {
    const params = {
      startDate: startDate.value || undefined,
      endDate: endDate.value || undefined,
    }
    const [s, t, src, d] = await Promise.all([
      getSalesSummary(params),
      getSalesTrend(params),
      getOrderSource(params),
      getDailySales(params),
    ])
    summary.value = s
    trend.value = t
    source.value = src
    daily.value = d
  } finally {
    loading.value = false
  }
}

function exportReport(type: 'summary' | 'trend' | 'source' | 'daily') {
  exportCsv('/reports/sales/export', {
    type,
    startDate: startDate.value || undefined,
    endDate: endDate.value || undefined,
  })
}

function orderTypeName(type: number) {
  return type === 2 ? 'B2C 终端客户' : 'B2B 商户'
}

onMounted(() => {
  defaultRange()
  load()
})
</script>

<template>
  <div class="report-page">
    <a-card :bordered="false" class="toolbar-card">
      <a-space wrap>
        <span>开始日期</span>
        <a-date-picker v-model="startDate" value-format="YYYY-MM-DD" placeholder="开始日期" />
        <span>结束日期</span>
        <a-date-picker v-model="endDate" value-format="YYYY-MM-DD" placeholder="结束日期" />
        <a-button type="primary" :loading="loading" @click="load">查询</a-button>
        <a-button @click="exportReport('summary')">导出汇总</a-button>
        <a-button @click="exportReport('trend')">导出趋势</a-button>
        <a-button @click="exportReport('source')">导出来源</a-button>
        <a-button @click="exportReport('daily')">导出日快照</a-button>
      </a-space>
    </a-card>

    <a-row :gutter="[16, 16]" class="stat-row">
      <a-col
        v-for="stat in [
          { label: '订单数', value: summary?.orderCount ?? 0 },
          { label: '支付订单数', value: summary?.paidOrderCount ?? 0 },
          { label: '支付金额(元)', value: Number(summary?.paidAmount ?? 0) },
          { label: '客单价(元)', value: Number(summary?.avgOrderValue ?? 0) },
          { label: '毛利(元)', value: Number(summary?.grossProfit ?? 0) },
          { label: '退款金额(元)', value: Number(summary?.refundAmount ?? 0) },
          { label: '复购率(%)', value: Number(summary?.repurchaseRate ?? 0) },
          { label: '复购客户数', value: summary?.repeatCustomers ?? 0 },
        ]" :key="stat.label" :xs="24" :sm="12" :lg="6"
      >
        <a-card :bordered="false">
          <a-statistic :title="stat.label" :value="stat.value" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="14">
        <a-card :bordered="false" title="销售趋势（近 30 天日支付金额）" :loading="loading">
          <SimpleBar
            :items="trend.map((item) => ({ label: item.bizDate.slice(5), value: Number(item.paidAmount) }))"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="10">
        <a-card :bordered="false" title="订单来源" :loading="loading">
          <SimpleBar
            :items="source.map((item) => ({
              label: orderTypeName(item.orderType),
              value: Number(item.paidAmount),
            }))"
            color="rgb(var(--green-6))"
          />
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false" title="每日销售快照（定时生成）" :loading="loading">
      <a-table row-key="bizDate" :data="daily" :pagination="false" :scroll="{ x: 800 }">
        <template #columns>
          <a-table-column title="业务日期" data-index="bizDate" :width="120" />
          <a-table-column title="订单数" data-index="orderCount" :width="100" />
          <a-table-column title="支付订单数" data-index="paidOrderCount" :width="110" />
          <a-table-column title="支付金额" data-index="paidAmount" :width="120" />
          <a-table-column title="毛利" data-index="grossProfit" :width="120" />
          <a-table-column title="退款金额" data-index="refundAmount" :width="120" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.toolbar-card {
  margin-bottom: 16px;
}
.stat-row {
  margin-bottom: 16px;
}
</style>

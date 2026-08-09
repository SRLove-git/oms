<script setup lang="ts">
import { onMounted, ref } from 'vue'

import {
  exportCsv,
  getAfterSalesTypeStats,
  getReasonDistribution,
  getRepairDuration,
  getReturnRate,
  type ReasonDistribution,
  type RepairDuration,
  type ReturnRate,
  type TypeStats,
} from '@/api/reports'
import SimpleBar from '@/components/SimpleBar.vue'

const loading = ref(false)
const startDate = ref('')
const endDate = ref('')

const typeStats = ref<TypeStats[]>([])
const reasons = ref<ReasonDistribution[]>([])
const repair = ref<RepairDuration | null>(null)
const returnRate = ref<ReturnRate | null>(null)

const TYPE_NAMES: Record<number, string> = {
  1: '退货',
  2: '换货',
  3: '维修',
}

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
    const [types, reason, repairDuration, rate] = await Promise.all([
      getAfterSalesTypeStats(params),
      getReasonDistribution({ ...params, topN: 10 }),
      getRepairDuration(params),
      getReturnRate(params),
    ])
    typeStats.value = types
    reasons.value = reason
    repair.value = repairDuration
    returnRate.value = rate
  } finally {
    loading.value = false
  }
}

function typeName(type: number) {
  return TYPE_NAMES[type] ?? '未知'
}

function exportReport(type: 'type' | 'reason' | 'repair' | 'return-rate') {
  exportCsv('/reports/aftersales/export', {
    type,
    startDate: startDate.value || undefined,
    endDate: endDate.value || undefined,
  })
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
        <a-button @click="exportReport('type')">导出类型统计</a-button>
        <a-button @click="exportReport('reason')">导出原因分布</a-button>
        <a-button @click="exportReport('repair')">导出维修时长</a-button>
        <a-button @click="exportReport('return-rate')">导出退货率</a-button>
      </a-space>
    </a-card>

    <a-row :gutter="[16, 16]" class="stat-row">
      <a-col
        v-for="stat in [
          { label: '退货率(%)', value: Number(returnRate?.rate ?? 0) },
          { label: '退货单量', value: returnRate?.returnCount ?? 0 },
          { label: '已完成订单', value: returnRate?.completedOrderCount ?? 0 },
          { label: '维修单量', value: repair?.repairCount ?? 0 },
          { label: '维修平均时长(分钟)', value: repair?.avgMinutes ?? 0 },
        ]" :key="stat.label" :xs="24" :sm="12" :lg="6"
      >
        <a-card :bordered="false">
          <a-statistic :title="stat.label" :value="stat.value" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" title="售后类型统计" :loading="loading">
          <a-table row-key="type" :data="typeStats" :pagination="false" :scroll="{ x: 700 }">
            <template #columns>
              <a-table-column title="类型" :width="100">
                <template #cell="{ record }">{{ typeName(record.type) }}</template>
              </a-table-column>
              <a-table-column title="单量" data-index="count" :width="90" />
              <a-table-column title="金额" data-index="totalAmount" :width="120" />
              <a-table-column title="已完成" data-index="completedCount" :width="90" />
              <a-table-column title="退款金额" data-index="refundedAmount" :width="120" />
            </template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" title="售后原因分布" :loading="loading">
          <SimpleBar
            :items="reasons.map((item) => ({ label: item.reason, value: item.count }))"
            color="rgb(var(--red-6))"
          />
        </a-card>
      </a-col>
    </a-row>
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

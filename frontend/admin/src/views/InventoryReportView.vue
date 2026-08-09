<script setup lang="ts">
import { onMounted, ref } from 'vue'

import {
  exportCsv,
  getExpiryDistribution,
  getSlowMoving,
  getStockSummary,
  getTurnover,
  getWarehouseStock,
  type ExpiryBucket,
  type SlowMovingItem,
  type StockSummary,
  type TurnoverItem,
  type WarehouseStock,
} from '@/api/reports'
import SimpleBar from '@/components/SimpleBar.vue'

const loading = ref(false)
const startDate = ref('')
const endDate = ref('')

const stockSummary = ref<StockSummary | null>(null)
const warehouses = ref<WarehouseStock[]>([])
const expiry = ref<ExpiryBucket[]>([])
const turnover = ref<TurnoverItem[]>([])
const slowMoving = ref<SlowMovingItem[]>([])

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
    const [summary, wh, exp, to, slow] = await Promise.all([
      getStockSummary(),
      getWarehouseStock(),
      getExpiryDistribution(),
      getTurnover({ ...params, topN: 10 }),
      getSlowMoving({ days: 90, limit: 50 }),
    ])
    stockSummary.value = summary
    warehouses.value = wh
    expiry.value = exp
    turnover.value = to
    slowMoving.value = slow
  } finally {
    loading.value = false
  }
}

function exportReport(type: 'warehouse-stock' | 'expiry' | 'turnover' | 'slow-moving') {
  exportCsv('/reports/inventory/export', {
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
        <a-button @click="exportReport('warehouse-stock')">导出库存</a-button>
        <a-button @click="exportReport('expiry')">导出效期</a-button>
        <a-button @click="exportReport('turnover')">导出周转</a-button>
        <a-button @click="exportReport('slow-moving')">导出滞销</a-button>
      </a-space>
    </a-card>

    <a-row :gutter="[16, 16]" class="stat-row">
      <a-col
        v-for="stat in [
          { label: '总库存', value: stockSummary?.totalQuantity ?? 0 },
          { label: '预占库存', value: stockSummary?.reservedQuantity ?? 0 },
          { label: '冻结库存', value: stockSummary?.frozenQuantity ?? 0 },
          { label: 'SKU 数', value: stockSummary?.skuCount ?? 0 },
          { label: '仓库数', value: stockSummary?.warehouseCount ?? 0 },
        ]" :key="stat.label" :xs="24" :sm="12" :lg="6"
      >
        <a-card :bordered="false">
          <a-statistic :title="stat.label" :value="stat.value" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" title="仓库库存" :loading="loading">
          <SimpleBar
            :items="warehouses.map((item) => ({ label: item.warehouseName, value: item.totalQuantity }))"
          />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" title="效期分布" :loading="loading">
          <SimpleBar
            :items="expiry.map((item) => ({ label: item.bucket, value: item.quantity }))"
            color="rgb(var(--orange-6))"
          />
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false" title="库存周转 TOP10（出库量 / 当前库存）" :loading="loading">
      <a-table row-key="skuId" :data="turnover" :pagination="false" :scroll="{ x: 800 }">
        <template #columns>
          <a-table-column title="SKU 编码" data-index="skuNo" :width="140" />
          <a-table-column title="SKU 名称" data-index="skuName" :width="200" />
          <a-table-column title="出库量" data-index="outboundQuantity" :width="110" />
          <a-table-column title="当前库存" data-index="currentStock" :width="110" />
          <a-table-column title="周转率" data-index="turnoverRate" :width="110" />
        </template>
      </a-table>
    </a-card>

    <a-card :bordered="false" title="滞销预警（90 天未动销且有库存）" :loading="loading">
      <a-table row-key="skuId" :data="slowMoving" :pagination="false" :scroll="{ x: 800 }">
        <template #columns>
          <a-table-column title="SKU 编码" data-index="skuNo" :width="140" />
          <a-table-column title="SKU 名称" data-index="skuName" :width="220" />
          <a-table-column title="当前库存" data-index="currentStock" :width="110" />
          <a-table-column title="最后动销时间">
            <template #cell="{ record }">{{ record.lastSaleAt ?? '从未动销' }}</template>
          </a-table-column>
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

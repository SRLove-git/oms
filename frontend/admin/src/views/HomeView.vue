<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { pageOrders } from '@/api/orders'
import { pageMerchants } from '@/api/merchants'
import { pageQualifications } from '@/api/qualifications'
import { pageSkus } from '@/api/skus'

const stats = ref([
  { label: '订单总数', value: 0 },
  { label: '商户总数', value: 0 },
  { label: '待审核资质', value: 0 },
  { label: '在售商品', value: 0 },
])

async function loadStats() {
  const [orders, merchants, qualifications, skus] = await Promise.all([
    pageOrders({ page: 1, size: 1 }),
    pageMerchants({ page: 1, size: 1 }),
    pageQualifications({ page: 1, size: 1 }),
    pageSkus({ page: 1, size: 1 }),
  ])
  stats.value = [
    { label: '订单总数', value: orders.total },
    { label: '商户总数', value: merchants.total },
    { label: '待审核资质', value: qualifications.total },
    { label: '在售商品', value: skus.total },
  ]
}

onMounted(loadStats)
</script>

<template>
  <div class="home">
    <a-card :bordered="false" class="welcome-card">
      <template #title>欢迎使用 OMS 管理端</template>
      P0 核心交易链路（下单 → 支付 → 发货）已可端到端演示。
    </a-card>

    <a-row :gutter="[16, 16]" class="stat-row">
      <a-col v-for="stat in stats" :key="stat.label" :xs="24" :sm="12" :lg="6">
        <a-card :bordered="false">
          <a-statistic :title="stat.label" :value="stat.value" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<style scoped>
.welcome-card {
  margin-bottom: 16px;
}
</style>

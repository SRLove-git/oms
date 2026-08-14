<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import {
  getAfterSalesTypeStats,
  getChannelStats,
  getExpiryDistribution,
  getReturnRate,
  getSalesSummary,
  getSalesTrend,
  getStockSummary,
  type ChannelStats,
  type SalesSummary,
  type TypeStats,
} from '@/api/reports'
import SimpleBar from '@/components/SimpleBar.vue'

const { t } = useI18n()

const loading = ref(false)
const summary = ref<SalesSummary | null>(null)
const trend = ref<{ label: string; value: number }[]>([])
const expiry = ref<{ label: string; value: number }[]>([])
const rawChannels = ref<ChannelStats[]>([])
const rawAftersales = ref<TypeStats[]>([])
const stockSummary = ref({ totalQuantity: 0, skuCount: 0 })
const returnRate = ref({ rate: '0' })

const CHANNEL_KEYS: Record<string, string> = {
  wechat: 'home.channels.wechat',
  alipay: 'home.channels.alipay',
  visa: 'home.channels.visa',
  mastercard: 'home.channels.mastercard',
  balance: 'home.channels.balance',
  mock: 'home.channels.mock',
}

const TYPE_KEYS: Record<number, string> = {
  1: 'home.aftersalesTypes.1',
  2: 'home.aftersalesTypes.2',
  3: 'home.aftersalesTypes.3',
}

const channels = computed(() =>
  rawChannels.value.map((item) => ({
    label: CHANNEL_KEYS[item.channel] ? t(CHANNEL_KEYS[item.channel]) : item.channel,
    value: Number(item.successAmount),
  })),
)

const aftersales = computed(() =>
  rawAftersales.value.map((item) => ({
    label: TYPE_KEYS[item.type] ? t(TYPE_KEYS[item.type]) : String(item.type),
    value: item.count,
  })),
)

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
    const [s, tTrend, e, c, a, stock, rate] = await Promise.all([
      getSalesSummary(params),
      getSalesTrend(params),
      getExpiryDistribution(),
      getChannelStats(params),
      getAfterSalesTypeStats(params),
      getStockSummary(),
      getReturnRate(params),
    ])
    summary.value = s
    trend.value = tTrend.map((item) => ({
      label: item.bizDate.slice(5),
      value: Number(item.paidAmount),
    }))
    expiry.value = e.map((item) => ({ label: item.bucket, value: item.quantity }))
    rawChannels.value = c
    rawAftersales.value = a
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
      <template #title>{{ t('home.title') }}</template>
      {{ t('home.description') }}
    </a-card>

    <a-row :gutter="[16, 16]" class="stat-row">
      <a-col
        v-for="stat in [
          { label: t('home.paidAmount'), value: Number(summary?.paidAmount ?? 0) },
          { label: t('home.orderCount'), value: summary?.orderCount ?? 0 },
          { label: t('home.avgOrderValue'), value: Number(summary?.avgOrderValue ?? 0) },
          { label: t('home.repurchaseRate'), value: Number(summary?.repurchaseRate ?? 0) },
          { label: t('home.totalStock'), value: stockSummary.totalQuantity },
          { label: t('home.stockSkuCount'), value: stockSummary.skuCount },
          { label: t('home.refundAmount'), value: Number(summary?.refundAmount ?? 0) },
          { label: t('home.returnRate'), value: Number(returnRate.rate) },
        ]" :key="stat.label" :xs="24" :sm="12" :lg="6"
      >
        <a-card :bordered="false">
          <a-statistic :title="stat.label" :value="stat.value" />
        </a-card>
      </a-col>
    </a-row>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" :title="t('home.salesTrend')" :loading="loading">
          <SimpleBar :items="trend" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" :title="t('home.expiryDistribution')" :loading="loading">
          <SimpleBar :items="expiry" color="rgb(var(--orange-6))" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" :title="t('home.channelAmount')" :loading="loading">
          <SimpleBar :items="channels" color="rgb(var(--purple-6))" />
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="12">
        <a-card :bordered="false" :title="t('home.aftersalesCount')" :loading="loading">
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

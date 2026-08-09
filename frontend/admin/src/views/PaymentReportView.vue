<script setup lang="ts">
import { onMounted, ref } from 'vue'

import {
  exportCsv,
  getChannelStats,
  getReconciliationStats,
  type ChannelStats,
  type ReconciliationStats,
} from '@/api/reports'
import SimpleBar from '@/components/SimpleBar.vue'

const loading = ref(false)
const startDate = ref('')
const endDate = ref('')

const channels = ref<ChannelStats[]>([])
const reconciliations = ref<ReconciliationStats[]>([])

const STATUS_NAMES: Record<number, string> = {
  1: '差异待处理',
  2: '已处理',
  3: '一致',
}

const CHANNEL_NAMES: Record<string, string> = {
  wechat: '微信',
  alipay: '支付宝',
  visa: 'Visa',
  mastercard: 'Mastercard',
  balance: '余额',
  mock: 'Mock 渠道',
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
    const [c, r] = await Promise.all([
      getChannelStats(params),
      getReconciliationStats(params),
    ])
    channels.value = c
    reconciliations.value = r
  } finally {
    loading.value = false
  }
}

function channelName(channel: string) {
  return CHANNEL_NAMES[channel] ?? channel
}

function exportReport(type: 'channel' | 'reconciliation') {
  exportCsv('/reports/payments/export', {
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
        <a-button @click="exportReport('channel')">导出渠道统计</a-button>
        <a-button @click="exportReport('reconciliation')">导出对账统计</a-button>
      </a-space>
    </a-card>

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="14">
        <a-card :bordered="false" title="渠道交易统计" :loading="loading">
          <a-table row-key="channel" :data="channels" :pagination="false" :scroll="{ x: 900 }">
            <template #columns>
              <a-table-column title="渠道" :width="110">
                <template #cell="{ record }">{{ channelName(record.channel) }}</template>
              </a-table-column>
              <a-table-column title="总笔数" data-index="totalCount" :width="90" />
              <a-table-column title="成功笔数" data-index="successCount" :width="100" />
              <a-table-column title="成功金额" data-index="successAmount" :width="120" />
              <a-table-column title="失败笔数" data-index="failCount" :width="90" />
              <a-table-column title="退款笔数" data-index="refundCount" :width="90" />
              <a-table-column title="退款金额" data-index="refundAmount" :width="120" />
              <a-table-column title="退款率(%)" data-index="refundRate" :width="100" />
            </template>
          </a-table>
        </a-card>
      </a-col>
      <a-col :xs="24" :lg="10">
        <a-card :bordered="false" title="渠道成功金额占比" :loading="loading">
          <SimpleBar
            :items="channels.map((item) => ({
              label: channelName(item.channel),
              value: Number(item.successAmount),
            }))"
            color="rgb(var(--purple-6))"
          />
        </a-card>
      </a-col>
    </a-row>

    <a-card :bordered="false" title="对账差异统计" :loading="loading">
      <a-table row-key="id" :data="reconciliations" :pagination="false" :scroll="{ x: 900 }">
        <template #columns>
          <a-table-column title="渠道" :width="140">
            <template #cell="{ record }">{{ channelName(record.channel) }}</template>
          </a-table-column>
          <a-table-column title="状态" :width="120">
            <template #cell="{ record }">
              <a-tag :color="record.status === 3 ? 'green' : record.status === 1 ? 'orange' : 'gray'">
                {{ STATUS_NAMES[record.status] }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="记录数" data-index="recordCount" :width="100" />
          <a-table-column title="渠道金额" data-index="channelAmount" :width="130" />
          <a-table-column title="本地金额" data-index="localAmount" :width="130" />
          <a-table-column title="差异笔数" data-index="diffCount" :width="100" />
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<style scoped>
.toolbar-card {
  margin-bottom: 16px;
}
</style>

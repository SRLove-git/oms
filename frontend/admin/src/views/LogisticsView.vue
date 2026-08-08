<script setup lang="ts">
import { ref } from 'vue'
import { Message } from '@arco-design/web-vue'

import { getLogisticsByOrder, type LogisticsRecord } from '@/api/integrations'

const STATUS_NAMES: Record<string, string> = {
  picked_up: '已揽收',
  in_transit: '运输中',
  out_for_delivery: '派送中',
  signed: '已签收',
  exception: '异常',
}

const orderNo = ref('')
const loading = ref(false)
const detail = ref<LogisticsRecord | null>(null)

async function query() {
  if (!orderNo.value) {
    Message.warning('请输入订单号')
    return
  }
  loading.value = true
  try {
    detail.value = await getLogisticsByOrder(orderNo.value)
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <a-card :bordered="false" title="物流轨迹">
    <a-space class="toolbar">
      <a-input v-model="orderNo" placeholder="输入订单号查询物流轨迹" style="width: 300px" />
      <a-button type="primary" :loading="loading" @click="query">查询</a-button>
    </a-space>

    <template v-if="detail">
      <a-descriptions :column="3" bordered size="small">
        <a-descriptions-item label="运单号">{{ detail.trackingNo }}</a-descriptions-item>
        <a-descriptions-item label="承运商">{{ detail.carrier }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag color="arcoblue">{{ STATUS_NAMES[detail.status] || detail.status }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="订单号">{{ detail.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="更新时间" :span="2">{{ detail.updatedAt }}</a-descriptions-item>
      </a-descriptions>

      <a-timeline class="timeline">
        <a-timeline-item v-for="(trace, index) in detail.trace" :key="index">
          {{ trace }}
        </a-timeline-item>
      </a-timeline>
    </template>
  </a-card>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.timeline {
  margin-top: 24px;
}
</style>

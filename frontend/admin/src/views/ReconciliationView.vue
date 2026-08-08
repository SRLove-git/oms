<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'

import {
  handleReconciliation,
  pageReconciliation,
  runReconciliation,
  type ReconciliationRecord,
} from '@/api/reconciliation'

const STATUS_NAMES: Record<number, string> = {
  1: '差异待处理',
  2: '已处理',
  3: '一致',
}

const DIFF_NAMES: Record<string, string> = {
  LOCAL_ONLY: '本地有/渠道无',
  CHANNEL_ONLY: '渠道有/本地无',
  AMOUNT_MISMATCH: '金额不一致',
}

const loading = ref(false)
const list = ref<ReconciliationRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const running = ref(false)
const bizDate = ref('')
const simulateDiff = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await pageReconciliation({
      bizDate: bizDate.value || undefined,
      page: page.value,
      size: pageSize.value,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function doRun() {
  running.value = true
  try {
    const res = await runReconciliation({
      bizDate: bizDate.value || new Date().toISOString().slice(0, 10),
      channel: 'mock',
      simulateDiff: simulateDiff.value,
    })
    Message.success(`对账完成：差异 ${res.diffCount} 笔`)
    load()
  } finally {
    running.value = false
  }
}

function doHandle(row: ReconciliationRecord) {
  Modal.confirm({
    title: '处理差异',
    content: `确认已处理 ${row.bizDate} ${row.channel} 渠道的 ${row.diffCount} 笔差异？`,
    onOk: async () => {
      await handleReconciliation(row.id)
      Message.success('已处理')
      load()
    },
  })
}

function onPageChange(value: number) {
  page.value = value
  load()
}

onMounted(load)
</script>

<template>
  <a-card :bordered="false" title="支付对账">
    <a-space class="toolbar">
      <a-input v-model="bizDate" placeholder="对账日期 YYYY-MM-DD（默认今天）" style="width: 240px" />
      <a-checkbox v-model="simulateDiff">模拟渠道差异</a-checkbox>
      <a-button type="primary" :loading="running" @click="doRun">执行对账</a-button>
      <a-button @click="load">刷新</a-button>
    </a-space>

    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 1000 }">
      <template #columns>
        <a-table-column title="对账日期" data-index="bizDate" :width="120" />
        <a-table-column title="渠道" data-index="channel" :width="90" />
        <a-table-column title="渠道金额" data-index="channelAmount" :width="110" />
        <a-table-column title="本地金额" data-index="localAmount" :width="110" />
        <a-table-column title="差异笔数" data-index="diffCount" :width="100" />
        <a-table-column title="状态" :width="110">
          <template #cell="{ record }">
            <a-tag :color="record.status === 3 ? 'green' : record.status === 1 ? 'orange' : 'gray'">
              {{ STATUS_NAMES[record.status] }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="差异明细" :width="420">
          <template #cell="{ record }">
            <div v-if="record.diffs.length">
              <div v-for="diff in record.diffs" :key="diff.paymentNo" class="diff-line">
                {{ diff.paymentNo }}：{{ DIFF_NAMES[diff.type] || diff.type }}
              </div>
            </div>
            <span v-else>-</span>
          </template>
        </a-table-column>
        <a-table-column title="操作" :width="100" fixed="right">
          <template #cell="{ record }">
            <a-button v-if="record.status === 1" size="mini" type="primary" @click="doHandle(record)">
              处理
            </a-button>
          </template>
        </a-table-column>
      </template>
    </a-table>

    <a-pagination
      class="pagination"
      :total="total"
      :current="page"
      :page-size="pageSize"
      show-total
      @change="onPageChange"
    />
  </a-card>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.diff-line {
  font-size: 12px;
  color: rgb(var(--orange-6));
}
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

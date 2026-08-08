<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { pageAuditLogs } from '@/api/audit'
import type { AuditLogRecord } from '@/api/audit'

const loading = ref(false)
const list = ref<AuditLogRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

async function load() {
  loading.value = true
  try {
    const res = await pageAuditLogs({ page: page.value, size: pageSize.value })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function onPageChange(value: number) {
  page.value = value
  load()
}

onMounted(load)
</script>

<template>
  <a-card :bordered="false" title="审计日志">
    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 1000 }">
      <template #columns>
        <a-table-column title="时间" data-index="createdAt" :width="180" />
        <a-table-column title="操作人" data-index="operatorName" :width="120" />
        <a-table-column title="模块" data-index="module" :width="120" />
        <a-table-column title="动作" data-index="action" :width="100" />
        <a-table-column title="业务 ID" data-index="bizId" :width="100" />
        <a-table-column title="操作前" data-index="beforeData" />
        <a-table-column title="操作后" data-index="afterData" />
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
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

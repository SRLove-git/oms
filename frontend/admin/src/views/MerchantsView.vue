<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'

import { pageMerchants, reviewMerchant } from '@/api/merchants'
import type { MerchantRecord } from '@/api/merchants'

const STATUS_NAMES: Record<number, string> = {
  1: '待审核',
  2: '已启用',
  3: '已停用',
}

const loading = ref(false)
const list = ref<MerchantRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const reviewVisible = ref(false)
const reviewForm = reactive({
  id: 0,
  name: '',
  approved: true,
  reason: '',
})

async function load() {
  loading.value = true
  try {
    const res = await pageMerchants({ page: page.value, size: pageSize.value })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openReview(row: MerchantRecord) {
  reviewForm.id = row.id
  reviewForm.name = row.name
  reviewForm.approved = true
  reviewForm.reason = ''
  reviewVisible.value = true
}

async function submitReview() {
  await reviewMerchant(reviewForm.id, reviewForm.approved, reviewForm.reason)
  Message.success(reviewForm.approved ? '已启用商户' : '已驳回/停用商户')
  reviewVisible.value = false
  load()
}

function onPageChange(value: number) {
  page.value = value
  load()
}

onMounted(load)
</script>

<template>
  <a-card :bordered="false" title="商户管理">
    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 800 }">
      <template #columns>
        <a-table-column title="商户编号" data-index="merchantNo" :width="160" />
        <a-table-column title="商户名称" data-index="name" />
        <a-table-column title="联系人" data-index="contactName" :width="120" />
        <a-table-column title="联系电话" data-index="contactPhone" :width="140" />
        <a-table-column title="状态" :width="90">
          <template #cell="{ record }">{{ STATUS_NAMES[record.status] }}</template>
        </a-table-column>
        <a-table-column title="创建时间" data-index="createdAt" :width="180" />
        <a-table-column title="操作" :width="100" fixed="right">
          <template #cell="{ record }">
            <a-button size="mini" type="primary" @click="openReview(record)">审核</a-button>
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

    <a-modal v-model:visible="reviewVisible" :title="`商户审核：${reviewForm.name}`" @ok="submitReview">
      <a-form layout="vertical" :model="reviewForm">
        <a-form-item label="审核结果">
          <a-switch v-model="reviewForm.approved" type="round">
            <template #checked>通过</template>
            <template #unchecked>驳回</template>
          </a-switch>
        </a-form-item>
        <a-form-item label="审核意见">
          <a-textarea v-model="reviewForm.reason" placeholder="请输入审核意见" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

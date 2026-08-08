<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'

import { pageQualifications, reviewQualification } from '@/api/qualifications'
import type { QualificationRecord } from '@/api/qualifications'

const TYPE_NAMES: Record<number, string> = {
  1: '注册证',
  2: '生产许可证',
  3: '经营许可证',
  4: '备案凭证',
}

const STATUS_NAMES: Record<number, string> = {
  1: '待审核',
  2: '已通过',
  3: '已驳回',
  4: '已过期',
}

const loading = ref(false)
const list = ref<QualificationRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const reviewVisible = ref(false)
const reviewForm = reactive({
  id: 0,
  no: '',
  approved: true,
  reason: '',
})

async function load() {
  loading.value = true
  try {
    const res = await pageQualifications({ page: page.value, size: pageSize.value })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function openReview(row: QualificationRecord) {
  reviewForm.id = row.id
  reviewForm.no = row.qualificationNo
  reviewForm.approved = true
  reviewForm.reason = ''
  reviewVisible.value = true
}

async function submitReview() {
  await reviewQualification(reviewForm.id, reviewForm.approved, reviewForm.reason)
  Message.success('审核完成')
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
  <a-card :bordered="false" title="资质管理">
    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 900 }">
      <template #columns>
        <a-table-column title="资质编号" data-index="qualificationNo" :width="160" />
        <a-table-column title="商户 ID" data-index="merchantId" :width="90" />
        <a-table-column title="类型" :width="110">
          <template #cell="{ record }">{{ TYPE_NAMES[record.qualificationType] }}</template>
        </a-table-column>
        <a-table-column title="有效期至" data-index="expireAt" :width="120" />
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

    <a-modal v-model:visible="reviewVisible" :title="`资质审核：${reviewForm.no}`" @ok="submitReview">
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

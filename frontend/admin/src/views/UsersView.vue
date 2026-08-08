<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'

import { createUser, pageUsers } from '@/api/users'
import type { UserRecord } from '@/api/users'

const TYPE_NAMES: Record<number, string> = {
  1: '平台运营',
  2: '商户',
  3: '终端客户',
}

const loading = ref(false)
const list = ref<UserRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const createVisible = ref(false)
const createForm = reactive({
  username: '',
  password: '',
  realName: '',
  phone: '',
  email: '',
  userType: 1,
  merchantId: undefined as number | undefined,
})

async function load() {
  loading.value = true
  try {
    const res = await pageUsers({ page: page.value, size: pageSize.value })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  await createUser({
    username: createForm.username,
    password: createForm.password,
    realName: createForm.realName,
    phone: createForm.phone,
    email: createForm.email,
    userType: createForm.userType,
    merchantId: createForm.merchantId,
  })
  Message.success('用户已创建')
  createVisible.value = false
  Object.assign(createForm, {
    username: '',
    password: '',
    realName: '',
    phone: '',
    email: '',
    userType: 1,
    merchantId: undefined,
  })
  load()
}

function onPageChange(value: number) {
  page.value = value
  load()
}

onMounted(load)
</script>

<template>
  <a-card :bordered="false" title="用户管理">
    <a-space class="toolbar">
      <a-button type="primary" @click="createVisible = true">新建用户</a-button>
    </a-space>

    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 900 }">
      <template #columns>
        <a-table-column title="用户名" data-index="username" :width="140" />
        <a-table-column title="姓名" data-index="realName" :width="120" />
        <a-table-column title="手机号" data-index="phone" :width="140" />
        <a-table-column title="类型" :width="100">
          <template #cell="{ record }">{{ TYPE_NAMES[record.userType] }}</template>
        </a-table-column>
        <a-table-column title="商户 ID" data-index="merchantId" :width="90" />
        <a-table-column title="状态" :width="90">
          <template #cell="{ record }">{{ record.status === 1 ? '启用' : '停用' }}</template>
        </a-table-column>
        <a-table-column title="创建时间" data-index="createdAt" :width="180" />
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

    <a-modal v-model:visible="createVisible" title="新建用户" @ok="submitCreate">
      <a-form layout="vertical" :model="createForm">
        <a-form-item label="用户名" required>
          <a-input v-model="createForm.username" />
        </a-form-item>
        <a-form-item label="密码" required>
          <a-input-password v-model="createForm.password" />
        </a-form-item>
        <a-form-item label="姓名">
          <a-input v-model="createForm.realName" />
        </a-form-item>
        <a-form-item label="手机号">
          <a-input v-model="createForm.phone" />
        </a-form-item>
        <a-form-item label="类型">
          <a-select
            v-model="createForm.userType"
            :options="Object.entries(TYPE_NAMES).map(([value, label]) => ({ value: Number(value), label }))"
          />
        </a-form-item>
        <a-form-item label="商户 ID">
          <a-input-number v-model="createForm.merchantId" :min="1" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </a-card>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}

.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

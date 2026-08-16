<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'

import { createSku, deleteSku, pageSkus, updateSkuStatus } from '@/api/skus'
import type { SkuRecord } from '@/api/skus'

const loading = ref(false)
const list = ref<SkuRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const keyword = ref('')
const createVisible = ref(false)
const createForm = reactive({
  spuNo: '',
  spuName: '',
  skuNo: '',
  name: '',
  spec: '',
  price: 0,
  udi: '',
  registrationNo: '',
})

async function load() {
  loading.value = true
  try {
    const res = await pageSkus({ keyword: keyword.value, page: page.value, size: pageSize.value })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function submitCreate() {
  await createSku({
    spuNo: createForm.spuNo,
    spuName: createForm.spuName,
    skuNo: createForm.skuNo,
    name: createForm.name,
    spec: createForm.spec,
    price: createForm.price,
    udi: createForm.udi,
    registrationNo: createForm.registrationNo,
  })
  Message.success('商品已创建')
  createVisible.value = false
  Object.assign(createForm, { spuNo: '', spuName: '', skuNo: '', name: '', spec: '', price: 0, udi: '', registrationNo: '' })
  load()
}

async function toggleStatus(row: SkuRecord) {
  await updateSkuStatus(row.id, row.status === 1 ? 0 : 1)
  Message.success(row.status === 1 ? '已下架' : '已上架')
  load()
}

function doDelete(row: SkuRecord) {
  Modal.confirm({
    title: '删除商品',
    content: `确定彻底删除商品“${row.name}”（${row.skuNo}）？此操作不可恢复，且要求该商品无库存、无关联订单。`,
    onOk: async () => {
      await deleteSku(row.id)
      Message.success('商品已删除')
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
  <a-card :bordered="false" title="商品管理">
    <a-space class="toolbar">
      <a-input v-model="keyword" placeholder="搜索商品名称/SKU 编码" allow-clear style="width: 240px" />
      <a-button type="primary" @click="load">查询</a-button>
      <a-button type="outline" @click="createVisible = true">新建商品</a-button>
    </a-space>

    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 1000 }">
      <template #columns>
        <a-table-column title="SKU 编码" data-index="skuNo" :width="140" />
        <a-table-column title="SPU 编码" data-index="spuNo" :width="140" />
        <a-table-column title="商品名称" data-index="name" />
        <a-table-column title="规格" data-index="spec" :width="120" />
        <a-table-column title="注册证号" data-index="registrationNo" :width="160" />
        <a-table-column title="UDI" data-index="udi" :width="160" />
        <a-table-column title="价格" data-index="price" :width="100" />
        <a-table-column title="状态" :width="80">
          <template #cell="{ record }">{{ record.status === 1 ? '在售' : '下架' }}</template>
        </a-table-column>
        <a-table-column title="操作" :width="170" fixed="right">
          <template #cell="{ record }">
            <a-space>
              <a-button size="mini" :status="record.status === 1 ? 'warning' : undefined" @click="toggleStatus(record)">
                {{ record.status === 1 ? '下架' : '上架' }}
              </a-button>
              <a-button size="mini" status="danger" @click="doDelete(record)">删除</a-button>
            </a-space>
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

    <a-modal v-model:visible="createVisible" title="新建商品（SPU + SKU）" @ok="submitCreate">
      <a-form layout="vertical" :model="createForm">
        <a-form-item label="SPU 编码" required>
          <a-input v-model="createForm.spuNo" placeholder="如 SPU001" />
        </a-form-item>
        <a-form-item label="SPU 名称" required>
          <a-input v-model="createForm.spuName" />
        </a-form-item>
        <a-form-item label="SKU 编码" required>
          <a-input v-model="createForm.skuNo" placeholder="如 SKU001" />
        </a-form-item>
        <a-form-item label="商品名称" required>
          <a-input v-model="createForm.name" />
        </a-form-item>
        <a-form-item label="规格">
          <a-input v-model="createForm.spec" />
        </a-form-item>
        <a-form-item label="渠道价" required>
          <a-input-number v-model="createForm.price" :min="0" :precision="2" style="width: 100%" />
        </a-form-item>
        <a-form-item label="UDI">
          <a-input v-model="createForm.udi" />
        </a-form-item>
        <a-form-item label="注册证号">
          <a-input v-model="createForm.registrationNo" />
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

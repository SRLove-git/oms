<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'

import { inbound, pageInventories, pageTransactions } from '@/api/inventories'
import type { InventoryRecord, InventoryTransactionRecord } from '@/api/inventories'
import { createWarehouse, listWarehouses } from '@/api/warehouses'
import type { WarehouseRecord } from '@/api/warehouses'

const BIZ_TYPES: Record<number, string> = {
  1: '预占',
  2: '释放',
  3: '扣减',
  4: '回补',
  5: '入库',
  6: '出库',
  7: '冻结',
  8: '解冻',
  9: '盘盈',
  10: '盘亏',
  11: '报废',
}

const loading = ref(false)
const list = ref<InventoryRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const warehouses = ref<WarehouseRecord[]>([])
const warehouseFilter = ref<number | undefined>(undefined)
const inboundVisible = ref(false)
const warehouseVisible = ref(false)
const txVisible = ref(false)
const txList = ref<InventoryTransactionRecord[]>([])
const inboundForm = reactive({
  warehouseId: undefined as number | undefined,
  skuId: undefined as number | undefined,
  quantity: 1,
  batchNo: '',
  expireAt: '',
})
const warehouseForm = reactive({ code: '', name: '', address: '' })

async function loadWarehouses() {
  warehouses.value = await listWarehouses()
}

async function load() {
  loading.value = true
  try {
    const res = await pageInventories({
      warehouseId: warehouseFilter.value,
      page: page.value,
      size: pageSize.value,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function submitInbound() {
  await inbound({
    warehouseId: inboundForm.warehouseId!,
    skuId: inboundForm.skuId!,
    quantity: inboundForm.quantity,
    batchNo: inboundForm.batchNo,
    expireAt: inboundForm.expireAt || undefined,
  })
  Message.success('入库成功')
  inboundVisible.value = false
  Object.assign(inboundForm, { warehouseId: undefined, skuId: undefined, quantity: 1, batchNo: '', expireAt: '' })
  load()
}

async function submitWarehouse() {
  await createWarehouse(warehouseForm)
  Message.success('仓库已创建')
  warehouseVisible.value = false
  Object.assign(warehouseForm, { code: '', name: '', address: '' })
  loadWarehouses()
}

async function showTransactions(skuId: number) {
  const res = await pageTransactions({ skuId, page: 1, size: 20 })
  txList.value = res.records
  txVisible.value = true
}

function onPageChange(value: number) {
  page.value = value
  load()
}

onMounted(() => {
  loadWarehouses()
  load()
})
</script>

<template>
  <a-card :bordered="false" title="库存管理">
    <a-space class="toolbar">
      <a-select
        v-model="warehouseFilter"
        placeholder="全部仓库"
        allow-clear
        :options="warehouses.map((w) => ({ value: w.id, label: w.name }))"
        style="width: 180px"
      />
      <a-button type="primary" @click="load">查询</a-button>
      <a-button type="outline" @click="inboundVisible = true">入库</a-button>
      <a-button type="outline" @click="warehouseVisible = true">新建仓库</a-button>
    </a-space>

    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 900 }">
      <template #columns>
        <a-table-column title="仓库 ID" data-index="warehouseId" :width="90" />
        <a-table-column title="SKU" data-index="skuId" :width="90" />
        <a-table-column title="SKU 编码" data-index="skuNo" :width="140" />
        <a-table-column title="批次" data-index="batchNo" :width="120" />
        <a-table-column title="可用" data-index="quantity" :width="90" />
        <a-table-column title="预占" data-index="reservedQuantity" :width="90" />
        <a-table-column title="冻结" data-index="frozenQuantity" :width="90" />
        <a-table-column title="效期" data-index="expireAt" :width="120" />
        <a-table-column title="操作" :width="100" fixed="right">
          <template #cell="{ record }">
            <a-button size="mini" @click="showTransactions(record.skuId)">流水</a-button>
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

    <a-modal v-model:visible="inboundVisible" title="库存入库" @ok="submitInbound">
      <a-form layout="vertical" :model="inboundForm">
        <a-form-item label="仓库" required>
          <a-select
            v-model="inboundForm.warehouseId"
            :options="warehouses.map((w) => ({ value: w.id, label: w.name }))"
          />
        </a-form-item>
        <a-form-item label="SKU ID" required>
          <a-input-number v-model="inboundForm.skuId" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="数量" required>
          <a-input-number v-model="inboundForm.quantity" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="批次号">
          <a-input v-model="inboundForm.batchNo" />
        </a-form-item>
        <a-form-item label="效期">
          <a-date-picker v-model="inboundForm.expireAt" value-format="YYYY-MM-DD" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:visible="warehouseVisible" title="新建仓库" @ok="submitWarehouse">
      <a-form layout="vertical" :model="warehouseForm">
        <a-form-item label="仓库编码" required>
          <a-input v-model="warehouseForm.code" />
        </a-form-item>
        <a-form-item label="仓库名称" required>
          <a-input v-model="warehouseForm.name" />
        </a-form-item>
        <a-form-item label="地址">
          <a-input v-model="warehouseForm.address" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-drawer v-model:visible="txVisible" title="库存流水" :width="720" :footer="false">
      <a-table row-key="id" :data="txList" :pagination="false" :scroll="{ x: 700 }">
        <template #columns>
          <a-table-column title="业务单号" data-index="bizNo" :width="150" />
          <a-table-column title="类型" :width="80">
            <template #cell="{ record }">{{ BIZ_TYPES[record.bizType] }}</template>
          </a-table-column>
          <a-table-column title="变动" data-index="changeQuantity" :width="80" />
          <a-table-column title="变动前" data-index="beforeQuantity" :width="90" />
          <a-table-column title="变动后" data-index="afterQuantity" :width="90" />
          <a-table-column title="备注" data-index="remark" />
          <a-table-column title="时间" data-index="createdAt" :width="170" />
        </template>
      </a-table>
    </a-drawer>
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

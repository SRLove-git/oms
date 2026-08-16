<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'

import {
  auditOrder,
  cancelOrder,
  completeOrder,
  getOrder,
  pageOrders,
  shipOrder,
  signOrder,
} from '@/api/orders'
import type { OrderDetail, OrderSummary } from '@/api/orders'

const STATUS_NAMES: Record<number, string> = {
  1: '待支付',
  2: '已支付',
  3: '已审核',
  4: '已发货',
  5: '已签收',
  6: '已完成',
  7: '已取消',
  8: '售后处理中',
}

const loading = ref(false)
const list = ref<OrderSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const statusFilter = ref<number | undefined>(undefined)
const detailVisible = ref(false)
const detail = ref<OrderDetail | null>(null)
const shipVisible = ref(false)
const shipForm = reactive({ orderNo: '', trackingNo: '', carrier: 'SF' })

async function load() {
  loading.value = true
  try {
    const res = await pageOrders({
      status: statusFilter.value,
      page: page.value,
      size: pageSize.value,
    })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function showDetail(orderNo: string) {
  detail.value = await getOrder(orderNo)
  detailVisible.value = true
}

async function doAudit(orderNo: string) {
  await auditOrder(orderNo)
  Message.success('审核通过')
  load()
}

async function doSign(orderNo: string) {
  await signOrder(orderNo)
  Message.success('已确认签收')
  load()
}

async function doComplete(orderNo: string) {
  await completeOrder(orderNo)
  Message.success('订单已完成')
  load()
}

function doCancel(orderNo: string) {
  Modal.confirm({
    title: '取消订单',
    content: '确定取消该订单？待支付订单将释放库存。',
    onOk: async () => {
      await cancelOrder(orderNo, '管理员取消')
      Message.success('已取消')
      load()
    },
  })
}

function openShip(orderNo: string) {
  shipForm.orderNo = orderNo
  shipForm.trackingNo = ''
  shipVisible.value = true
}

async function submitShip() {
  await shipOrder(shipForm.orderNo, shipForm.trackingNo, shipForm.carrier)
  Message.success('已发货')
  shipVisible.value = false
  load()
}

function onPageChange(value: number) {
  page.value = value
  load()
}

function onSizeChange(value: number) {
  pageSize.value = value
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <a-card :bordered="false" title="订单管理">
    <a-space class="toolbar" :size="12">
      <a-select
        v-model="statusFilter"
        placeholder="全部状态"
        allow-clear
        :options="Object.entries(STATUS_NAMES).map(([value, label]) => ({ value: Number(value), label }))"
        style="width: 160px"
        @change="load"
      />
      <a-button type="primary" @click="load">查询</a-button>
    </a-space>

    <a-table
      row-key="id"
      :loading="loading"
      :data="list"
      :pagination="false"
      :scroll="{ x: 900 }"
    >
      <template #columns>
        <a-table-column title="订单号" data-index="orderNo" :width="180" />
        <a-table-column title="商户 ID" data-index="merchantId" :width="90" />
        <a-table-column title="类型" :width="80">
          <template #cell="{ record }">{{ record.orderType === 2 ? 'B2C' : 'B2B' }}</template>
        </a-table-column>
        <a-table-column title="状态" :width="90">
          <template #cell="{ record }">{{ STATUS_NAMES[record.status] }}</template>
        </a-table-column>
        <a-table-column title="金额" data-index="totalAmount" :width="110" />
        <a-table-column title="商品数" data-index="itemCount" :width="90" />
        <a-table-column title="创建时间" data-index="createdAt" :width="180" />
        <a-table-column title="操作" :width="320" fixed="right">
          <template #cell="{ record }">
            <a-space>
              <a-button size="mini" @click="showDetail(record.orderNo)">详情</a-button>
              <a-button v-if="record.status === 2" size="mini" type="primary" @click="doAudit(record.orderNo)">
                审核
              </a-button>
              <a-button v-if="record.status === 3" size="mini" type="primary" @click="openShip(record.orderNo)">
                发货
              </a-button>
              <a-button v-if="record.status === 4" size="mini" type="primary" @click="doSign(record.orderNo)">
                签收
              </a-button>
              <a-button v-if="record.status === 5" size="mini" type="primary" @click="doComplete(record.orderNo)">
                完成
              </a-button>
              <a-button v-if="record.status === 1" size="mini" status="danger" @click="doCancel(record.orderNo)">
                取消
              </a-button>
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
      show-page-size
      @change="onPageChange"
      @page-size-change="onSizeChange"
    />

    <a-modal v-model:visible="detailVisible" title="订单详情" :footer="false" :width="720">
      <a-descriptions v-if="detail" :column="2" bordered>
        <a-descriptions-item label="订单号">{{ detail.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ STATUS_NAMES[detail.status] }}</a-descriptions-item>
        <a-descriptions-item label="应付金额">{{ detail.payAmount }} {{ detail.currency }}</a-descriptions-item>
        <a-descriptions-item label="创建时间">{{ detail.createdAt }}</a-descriptions-item>
        <a-descriptions-item label="超时时间">{{ detail.timeoutAt }}</a-descriptions-item>
        <a-descriptions-item label="备注">{{ detail.remark || '-' }}</a-descriptions-item>
      </a-descriptions>

      <a-table v-if="detail" row-key="id" :data="detail.items" :pagination="false" class="detail-table">
        <template #columns>
          <a-table-column title="SKU" data-index="skuId" :width="80" />
          <a-table-column title="商品" data-index="skuName" />
          <a-table-column title="数量" data-index="quantity" :width="80" />
          <a-table-column title="单价" data-index="unitPrice" :width="100" />
          <a-table-column title="小计" data-index="totalPrice" :width="110" />
        </template>
      </a-table>

      <a-timeline v-if="detail" class="detail-timeline">
        <a-timeline-item
          v-for="(log, index) in detail.logs"
          :key="index"
          :label="log.createdAt"
        >
          {{ STATUS_NAMES[log.toStatus] }}（{{ log.operatorName || '-' }}）{{ log.remark || '' }}
        </a-timeline-item>
      </a-timeline>
    </a-modal>

    <a-modal v-model:visible="shipVisible" title="订单发货" @ok="submitShip">
      <a-form layout="vertical" :model="shipForm">
        <a-form-item label="运单号">
          <a-input v-model="shipForm.trackingNo" placeholder="请输入运单号" />
        </a-form-item>
        <a-form-item label="承运商">
          <a-select
            v-model="shipForm.carrier"
            :options="['SF', 'STO', 'ZTO', 'YTO'].map((c) => ({ value: c, label: c }))"
          />
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

.detail-table {
  margin-top: 16px;
}

.detail-timeline {
  margin-top: 16px;
}
</style>

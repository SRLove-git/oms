<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'

import {
  applyReturnOrder,
  cancelReturnOrder,
  createRepair,
  exchangeShip,
  getReturnOrder,
  pageReturnOrders,
  receiveReturnOrder,
  refundReturnOrder,
  repairProgress,
  reviewReturnOrder,
  type ReturnOrder,
  type ReturnOrderSummary,
} from '@/api/aftersales'
import { getOrder, type OrderItemRecord } from '@/api/orders'

const TYPE_NAMES: Record<number, string> = {
  1: '退货',
  2: '换货',
  3: '维修',
}

const STATUS_NAMES: Record<number, string> = {
  1: '待审核',
  2: '已通过',
  3: '已驳回',
  4: '收货质检',
  5: '退款中',
  6: '已完成',
  7: '已取消',
}

const loading = ref(false)
const list = ref<ReturnOrderSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const detailVisible = ref(false)
const detail = ref<ReturnOrder | null>(null)
const applyVisible = ref(false)
const applySubmitting = ref(false)
const applyItems = ref<OrderItemRecord[]>([])
const selectedIds = ref<number[]>([])
const applyForm = reactive({ orderNo: '', type: 1, reason: '' })

async function load() {
  loading.value = true
  try {
    const res = await pageReturnOrders({ page: page.value, size: pageSize.value })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function showDetail(row: ReturnOrderSummary) {
  detail.value = await getReturnOrder(row.returnNo)
  detailVisible.value = true
}

function openApply() {
  applyVisible.value = true
  applyForm.orderNo = ''
  applyForm.type = 1
  applyForm.reason = ''
  applyItems.value = []
  selectedIds.value = []
}

async function loadApplyItems() {
  if (!applyForm.orderNo) {
    Message.warning('请输入订单号')
    return
  }
  const order = await getOrder(applyForm.orderNo)
  applyItems.value = order.items
  selectedIds.value = []
}

function toggleApplyItem(id: number, checked: boolean) {
  selectedIds.value = checked
    ? [...new Set([...selectedIds.value, id])]
    : selectedIds.value.filter((value) => value !== id)
}

async function submitApply() {
  const selected = applyItems.value.filter((item) => selectedIds.value.includes(item.id))
  if (!applyForm.orderNo || selected.length === 0) {
    Message.warning('请选择订单与售后商品')
    return
  }
  applySubmitting.value = true
  try {
    await applyReturnOrder({
      orderNo: applyForm.orderNo,
      type: applyForm.type,
      reason: applyForm.reason,
      items: selected.map((item) => ({
        orderItemId: item.id,
        skuId: item.skuId,
        quantity: item.quantity,
      })),
    })
    Message.success('售后单已创建')
    applyVisible.value = false
    load()
  } finally {
    applySubmitting.value = false
  }
}

function doReview(row: ReturnOrderSummary, approved: boolean) {
  Modal.confirm({
    title: approved ? '审核通过' : '审核驳回',
    content: `确定${approved ? '通过' : '驳回'}售后单 ${row.returnNo}？`,
    onOk: async () => {
      await reviewReturnOrder(row.returnNo, approved, approved ? undefined : '不符合售后条件')
      Message.success(approved ? '已通过' : '已驳回')
      load()
    },
  })
}

function doReceive(row: ReturnOrderSummary) {
  Modal.confirm({
    title: '收货质检',
    content: `确认已收到退货并发起质检（合格后按售后类型继续流转）？`,
    onOk: async () => {
      await receiveReturnOrder(row.returnNo, true, '质检合格')
      Message.success('质检通过')
      load()
    },
  })
}

function doRefund(row: ReturnOrderSummary) {
  Modal.confirm({
    title: '确认退款',
    content: `确定对售后单 ${row.returnNo} 发起退款 ${row.totalAmount} SGD？（需先完成收货质检）`,
    onOk: async () => {
      await refundReturnOrder(row.returnNo, '', row.totalAmount)
      Message.success('退款成功')
      load()
    },
  })
}

function doExchangeShip(row: ReturnOrderSummary) {
  Modal.confirm({
    title: '换货发运',
    content: `确认售后单 ${row.returnNo} 的换货商品已发运？`,
    onOk: async () => {
      await exchangeShip(row.returnNo)
      Message.success('换货发运完成')
      load()
    },
  })
}

function doCancel(row: ReturnOrderSummary) {
  Modal.confirm({
    title: '取消售后单',
    content: `确定取消售后单 ${row.returnNo}？`,
    onOk: async () => {
      await cancelReturnOrder(row.returnNo)
      Message.success('已取消')
      load()
    },
  })
}

function doCreateRepair(row: ReturnOrderSummary) {
  Modal.confirm({
    title: '创建维修工单',
    content: '确认已收货并创建维修工单？（默认指派给维修员）',
    onOk: async () => {
      await createRepair(row.returnNo, {
        skuId: 0,
        faultDesc: '待维修',
        assignedTo: '维修员',
      })
      Message.success('维修工单已创建')
      load()
    },
  })
}

function doRepairComplete(row: ReturnOrderSummary) {
  Modal.confirm({
    title: '维修完成',
    content: `确认售后单 ${row.returnNo} 的维修已完成？`,
    onOk: async () => {
      await repairProgress(0, { action: 'complete', content: '维修完成' })
      Message.success('维修完成')
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
  <a-card :bordered="false" title="售后服务">
    <template #extra>
      <a-button type="primary" @click="openApply">新建售后</a-button>
    </template>
    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 1100 }">
      <template #columns>
        <a-table-column title="售后单号" data-index="returnNo" :width="180" />
        <a-table-column title="订单号" data-index="orderNo" :width="180" />
        <a-table-column title="类型" :width="80">
          <template #cell="{ record }">{{ TYPE_NAMES[record.type] }}</template>
        </a-table-column>
        <a-table-column title="状态" :width="100">
          <template #cell="{ record }">
            <a-tag :color="record.status === 6 ? 'green' : record.status === 3 || record.status === 7 ? 'red' : 'arcoblue'">
              {{ STATUS_NAMES[record.status] }}
            </a-tag>
          </template>
        </a-table-column>
        <a-table-column title="售后金额" data-index="totalAmount" :width="100" />
        <a-table-column title="原因" data-index="reason" />
        <a-table-column title="申请时间" data-index="createdAt" :width="180" />
        <a-table-column title="操作" :width="260" fixed="right">
          <template #cell="{ record }">
            <a-space>
              <a-button size="mini" @click="showDetail(record)">详情</a-button>
              <a-button
                v-if="record.status === 1"
                size="mini"
                type="primary"
                @click="doReview(record, true)"
              >
                通过
              </a-button>
              <a-button v-if="record.status === 1" size="mini" status="danger" @click="doReview(record, false)">
                驳回
              </a-button>
              <a-button v-if="record.status === 2" size="mini" @click="doReceive(record)">收货质检</a-button>
              <a-button v-if="record.status === 2 && record.type === 2" size="mini" @click="doExchangeShip(record)">
                换货发运
              </a-button>
              <a-button v-if="record.status === 2 && record.type === 3" size="mini" @click="doCreateRepair(record)">
                创建维修
              </a-button>
              <a-button v-if="record.status === 4 && record.type === 3" size="mini" @click="doRepairComplete(record)">
                维修完成
              </a-button>
              <a-button v-if="record.status === 5" size="mini" type="primary" @click="doRefund(record)">退款</a-button>
              <a-button
                v-if="record.status === 1 || record.status === 2 || record.status === 4"
                size="mini"
                status="danger"
                @click="doCancel(record)"
              >
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
      @change="onPageChange"
    />

    <a-modal
      v-model:visible="applyVisible"
      title="新建售后（支持部分商品）"
      :ok-loading="applySubmitting"
      @ok="submitApply"
    >
      <a-form layout="vertical" :model="applyForm">
        <a-form-item label="订单号" required>
          <a-input v-model="applyForm.orderNo" placeholder="请输入订单号" />
          <a-button style="margin-top: 8px" @click="loadApplyItems">加载订单商品</a-button>
        </a-form-item>
        <a-form-item label="售后类型" required>
          <a-select v-model="applyForm.type">
            <a-option :value="1">退货</a-option>
            <a-option :value="2">换货</a-option>
            <a-option :value="3">维修</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="售后原因">
          <a-textarea v-model="applyForm.reason" placeholder="请填写售后原因" />
        </a-form-item>
      </a-form>

      <a-table
        v-if="applyItems.length"
        row-key="id"
        :data="applyItems"
        :pagination="false"
        size="small"
      >
        <template #columns>
          <a-table-column title="" :width="40">
            <template #cell="{ record }">
              <a-checkbox
                :model-value="selectedIds.includes(record.id)"
                @change="(checked: boolean | (string | number | boolean)[]) => toggleApplyItem(record.id, checked === true)"
              />
            </template>
          </a-table-column>
          <a-table-column title="SKU ID" data-index="skuId" />
          <a-table-column title="商品名称" data-index="skuName" />
          <a-table-column title="数量" data-index="quantity" />
          <a-table-column title="单价" data-index="unitPrice" />
        </template>
      </a-table>
    </a-modal>

    <a-drawer :visible="detailVisible" :width="640" title="售后单详情" @cancel="detailVisible = false">
      <a-descriptions v-if="detail" :column="2" bordered size="small">
        <a-descriptions-item label="售后单号">{{ detail.returnNo }}</a-descriptions-item>
        <a-descriptions-item label="订单号">{{ detail.orderNo }}</a-descriptions-item>
        <a-descriptions-item label="类型">{{ TYPE_NAMES[detail.type] }}</a-descriptions-item>
        <a-descriptions-item label="状态">{{ STATUS_NAMES[detail.status] }}</a-descriptions-item>
        <a-descriptions-item label="金额">{{ detail.totalAmount }}</a-descriptions-item>
        <a-descriptions-item label="原因">{{ detail.reason || '-' }}</a-descriptions-item>
      </a-descriptions>

      <template v-if="detail && detail.items.length">
        <h4>售后明细</h4>
        <a-table row-key="id" :data="detail.items" :pagination="false" size="small">
          <template #columns>
            <a-table-column title="SKU ID" data-index="skuId" />
            <a-table-column title="数量" data-index="quantity" />
            <a-table-column title="单价" data-index="unitAmount" />
          </template>
        </a-table>
      </template>

      <template v-if="detail && detail.refunds.length">
        <h4>退款记录</h4>
        <a-table row-key="refundNo" :data="detail.refunds" :pagination="false" size="small">
          <template #columns>
            <a-table-column title="退款单号" data-index="refundNo" />
            <a-table-column title="金额" data-index="amount" />
            <a-table-column title="渠道交易号" data-index="channelTxnNo" />
          </template>
        </a-table>
      </template>

      <template v-if="detail && detail.repairs.length">
        <h4>维修工单</h4>
        <a-table row-key="id" :data="detail.repairs" :pagination="false" size="small">
          <template #columns>
            <a-table-column title="维修单号" data-index="repairNo" />
            <a-table-column title="故障描述" data-index="faultDesc" />
            <a-table-column title="费用" data-index="repairFee" />
            <a-table-column title="维修人" data-index="assignedTo" />
          </template>
        </a-table>
      </template>
    </a-drawer>
  </a-card>
</template>

<style scoped>
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

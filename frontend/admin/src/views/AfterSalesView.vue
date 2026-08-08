<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'

import {
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
    content: `确定对售后单 ${row.returnNo} 发起退款 ${row.totalAmount} 元？（需先完成收货质检）`,
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

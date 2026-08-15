<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'

import { pagePayments, refundPayment } from '@/api/payments'
import type { PaymentRecord } from '@/api/payments'

const STATUS_NAMES: Record<number, string> = {
  1: '待支付',
  2: '成功',
  3: '失败',
  4: '已关闭',
  5: '已退款',
}

const loading = ref(false)
const list = ref<PaymentRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

async function load() {
  loading.value = true
  try {
    const res = await pagePayments({ page: page.value, size: pageSize.value })
    list.value = res.records
    total.value = res.total
  } finally {
    loading.value = false
  }
}

function doRefund(row: PaymentRecord) {
  Modal.confirm({
    title: '发起退款',
    content: `确定对支付单 ${row.paymentNo} 退款 ${row.amount} SGD？`,
    onOk: async () => {
      await refundPayment(row.paymentNo, row.amount)
      Message.success('退款成功')
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
  <a-card :bordered="false" title="支付记录">
    <a-table row-key="id" :loading="loading" :data="list" :pagination="false" :scroll="{ x: 900 }">
      <template #columns>
        <a-table-column title="支付单号" data-index="paymentNo" :width="180" />
        <a-table-column title="订单号" data-index="orderNo" :width="180" />
        <a-table-column title="渠道" data-index="channel" :width="90" />
        <a-table-column title="金额" data-index="amount" :width="100" />
        <a-table-column title="状态" :width="90">
          <template #cell="{ record }">{{ STATUS_NAMES[record.status] }}</template>
        </a-table-column>
        <a-table-column title="渠道交易号" data-index="channelTxnNo" :width="160" />
        <a-table-column title="支付时间" data-index="paidAt" :width="180" />
        <a-table-column title="操作" :width="100" fixed="right">
          <template #cell="{ record }">
            <a-button
              v-if="record.status === 2"
              size="mini"
              status="danger"
              @click="doRefund(record)"
            >
              退款
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
.pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>

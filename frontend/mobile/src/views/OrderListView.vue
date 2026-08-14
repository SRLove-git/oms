<template>
  <div>
    <a-radio-group v-model="statusFilter" type="button" size="small" class="mb-12 order-filter">
      <a-radio v-for="item in filters" :key="item.value" :value="item.value">
        {{ item.label }}
      </a-radio>
    </a-radio-group>

    <div v-if="loading && list.length === 0" class="app-empty">{{ t('common.loading') }}</div>
    <div v-else-if="list.length === 0" class="app-empty">{{ t('common.empty') }}</div>

    <template v-else>
      <div
        v-for="order in list"
        :key="order.id"
        class="app-list-item"
        @click="goDetail(order.orderNo)"
      >
        <div class="app-list-item-header">
          <span class="app-list-item-title">{{ order.orderNo }}</span>
          <a-tag :color="statusColor(order.status)" size="small">
            {{ statusName(order.status) }}
          </a-tag>
        </div>
        <div class="app-list-item-row">
          <span>{{ t('orders.amount') }}</span>
          <span class="text-price">¥{{ formatPrice(order.payAmount) }}</span>
        </div>
        <div class="app-list-item-row">
          <span>{{ t('orders.itemCount') }}</span>
          <span>{{ order.itemCount }}</span>
        </div>
        <div class="app-list-item-row">
          <span>{{ t('orders.createdAt') }}</span>
          <span>{{ order.createdAt ?? '-' }}</span>
        </div>
        <div
          v-if="order.status === 1 || order.status === 4"
          class="app-list-item-actions"
          @click.stop
        >
          <a-button
            v-if="order.status === 1"
            type="primary"
            size="small"
            @click="openPay(order.orderNo)"
          >
            {{ t('orders.pay') }}
          </a-button>
          <a-button
            v-if="order.status === 1"
            status="danger"
            size="small"
            @click="handleCancel(order)"
          >
            {{ t('orders.cancel') }}
          </a-button>
          <a-button
            v-if="order.status === 4"
            type="primary"
            size="small"
            @click="handleSign(order.orderNo)"
          >
            {{ t('orders.sign') }}
          </a-button>
        </div>
      </div>

      <div class="text-center mt-12">
        <a-button v-if="list.length < total" :loading="loading" @click="loadMore">
          {{ t('common.loadMore') }}
        </a-button>
        <span v-else class="text-muted">{{ t('common.noMore') }}</span>
      </div>
    </template>

    <PayModal
      :visible="payVisible"
      :order-no="payOrderNo"
      @update:visible="payVisible = $event"
      @success="load(true)"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { cancelOrder, pageOrders, signOrder } from '@/api/orders'
import type { OrderSummary } from '@/api/orders'
import PayModal from '@/components/PayModal.vue'

const { t } = useI18n()
const router = useRouter()

const statusFilter = ref<number | 'all'>('all')
const list = ref<OrderSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

const payVisible = ref(false)
const payOrderNo = ref('')

const filters = computed(() => [
  { value: 'all', label: t('common.all') },
  { value: 1, label: t('orderStatus.1') },
  { value: 2, label: t('orderStatus.2') },
  { value: 4, label: t('orderStatus.4') },
  { value: 6, label: t('orderStatus.6') },
  { value: 7, label: t('orderStatus.7') },
])

function statusName(status: number) {
  return t(`orderStatus.${status}` as never) || String(status)
}

function statusColor(status: number) {
  if (status === 1) {
    return 'orange'
  }
  if (status === 4) {
    return 'arcoblue'
  }
  if (status === 6) {
    return 'green'
  }
  if (status === 7) {
    return 'gray'
  }
  return 'cyan'
}

function formatPrice(value: string) {
  return Number(value).toFixed(2)
}

async function load(reset: boolean) {
  loading.value = true
  try {
    const res = await pageOrders({
      status: statusFilter.value === 'all' ? undefined : Number(statusFilter.value),
      page: reset ? 1 : page.value,
      size: pageSize,
    })
    total.value = res.total
    if (reset) {
      list.value = res.records
      page.value = 1
    } else {
      list.value = [...list.value, ...res.records]
    }
  } finally {
    loading.value = false
  }
}

function loadMore() {
  page.value += 1
  load(false)
}

function goDetail(orderNo: string) {
  router.push({ name: 'order-detail', params: { orderNo } })
}

function openPay(orderNo: string) {
  payOrderNo.value = orderNo
  payVisible.value = true
}

function handleCancel(order: OrderSummary) {
  Modal.confirm({
    title: t('orders.cancel'),
    content: t('orders.cancelConfirm'),
    okButtonProps: { status: 'danger' },
    onOk: async () => {
      await cancelOrder(order.orderNo, '用户取消')
      Message.success(t('orders.cancelSuccess'))
      load(true)
    },
  })
}

async function handleSign(orderNo: string) {
  try {
    await signOrder(orderNo)
    Message.success(t('orders.signSuccess'))
    load(true)
  } catch {
    Message.error(t('common.failed'))
  }
}

onMounted(() => {
  load(true)
})
</script>

<style scoped>
.order-filter {
  display: flex;
  flex-wrap: wrap;
  row-gap: 8px;
}
</style>

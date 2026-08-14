<template>
  <FullPage :title="t('orders.detail')">
    <div v-if="loading" class="app-empty">{{ t('common.loading') }}</div>

    <template v-else-if="order">
      <div class="app-card">
        <div class="flex-between">
          <span class="order-no">{{ order.orderNo }}</span>
          <a-tag :color="statusColor(order.status)" size="small">
            {{ statusName(order.status) }}
          </a-tag>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('orders.createdAt') }}</span>
          <span class="app-cell-value">{{ order.createdAt ?? '-' }}</span>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('orders.currency') }}</span>
          <span class="app-cell-value">{{ order.currency }}</span>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('orders.totalAmount') }}</span>
          <span class="app-cell-value">¥{{ formatPrice(order.totalAmount) }}</span>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('orders.payAmount') }}</span>
          <span class="app-cell-value text-price">¥{{ formatPrice(order.payAmount) }}</span>
        </div>
        <div v-if="order.remark" class="app-cell">
          <span class="app-cell-label">{{ t('checkout.remark') }}</span>
          <span class="app-cell-value">{{ order.remark }}</span>
        </div>
      </div>

      <div class="app-card">
        <div class="app-section-title" style="margin-top: 0">{{ t('orders.items') }}</div>
        <div v-for="item in order.items" :key="item.id" class="order-item">
          <div class="order-item-main">
            <div class="order-item-name">{{ item.skuName }}</div>
            <div class="text-muted">¥{{ formatPrice(item.unitPrice) }} × {{ item.quantity }}</div>
          </div>
          <div class="text-price">¥{{ formatPrice(item.totalPrice) }}</div>
        </div>
      </div>

      <div v-if="order.logs && order.logs.length > 0" class="app-card">
        <div class="app-section-title" style="margin-top: 0">{{ t('orders.viewLogs') }}</div>
        <a-timeline>
          <a-timeline-item v-for="(log, index) in order.logs" :key="index">
            <div class="app-timeline-label">
              <span>{{ statusName(log.toStatus) }}</span>
              <span class="app-timeline-time">{{ log.createdAt ?? '' }}</span>
            </div>
            <div class="text-muted" style="font-size: 12px">
              {{ log.operatorName ?? '-' }}{{ log.remark ? ' · ' + log.remark : '' }}
            </div>
          </a-timeline-item>
        </a-timeline>
      </div>

      <div v-if="order.status === 1 || order.status === 4" class="app-fixed-footer order-actions">
        <template v-if="order.status === 1">
          <a-button type="primary" long @click="openPay">
            {{ t('orders.pay') }}
          </a-button>
          <a-button status="danger" long @click="handleCancel">
            {{ t('orders.cancel') }}
          </a-button>
        </template>
        <a-button v-else type="primary" long @click="handleSign">
          {{ t('orders.sign') }}
        </a-button>
      </div>
    </template>

    <PayModal
      :visible="payVisible"
      :order-no="order?.orderNo ?? ''"
      @update:visible="payVisible = $event"
      @success="reload"
    />
  </FullPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

import { cancelOrder, getOrder, signOrder } from '@/api/orders'
import type { OrderDetail } from '@/api/orders'
import FullPage from '@/components/FullPage.vue'
import PayModal from '@/components/PayModal.vue'

const { t } = useI18n()
const route = useRoute()

const loading = ref(false)
const order = ref<OrderDetail | null>(null)
const payVisible = ref(false)

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

async function reload() {
  loading.value = true
  try {
    order.value = await getOrder(String(route.params.orderNo))
  } catch {
    Message.error(t('common.failed'))
  } finally {
    loading.value = false
  }
}

function openPay() {
  payVisible.value = true
}

function handleCancel() {
  if (!order.value) {
    return
  }
  Modal.confirm({
    title: t('orders.cancel'),
    content: t('orders.cancelConfirm'),
    okButtonProps: { status: 'danger' },
    onOk: async () => {
      await cancelOrder(order.value!.orderNo, '用户取消')
      Message.success(t('orders.cancelSuccess'))
      reload()
    },
  })
}

async function handleSign() {
  if (!order.value) {
    return
  }
  try {
    await signOrder(order.value.orderNo)
    Message.success(t('orders.signSuccess'))
    reload()
  } catch {
    Message.error(t('common.failed'))
  }
}

onMounted(reload)
</script>

<style scoped>
.order-no {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.order-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  gap: 12px;
}

.order-item + .order-item {
  border-top: 1px solid var(--color-border-2);
}

.order-item-name {
  font-size: 14px;
  color: var(--color-text-1);
  margin-bottom: 4px;
}

.order-actions {
  display: flex;
  gap: 12px;
}
</style>

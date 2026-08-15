<template>
  <FullPage :title="t('aftersales.applyTitle')">
    <div class="app-card">
      <div class="app-cell" style="flex-wrap: wrap">
        <span class="app-cell-label">{{ t('aftersales.orderNo') }}</span>
        <div class="apply-order-input">
          <a-input
            v-model="orderNo"
            :placeholder="t('aftersales.orderNoPlaceholder')"
            allow-clear
          />
          <a-button size="small" :loading="loadingItems" @click="loadOrderItems">
            {{ t('aftersales.loadOrder') }}
          </a-button>
        </div>
      </div>

      <div class="app-cell">
        <span class="app-cell-label">{{ t('aftersales.type') }}</span>
        <a-radio-group v-model="type" type="button">
          <a-radio :value="1">{{ t('afterType.1') }}</a-radio>
          <a-radio :value="2">{{ t('afterType.2') }}</a-radio>
          <a-radio :value="3">{{ t('afterType.3') }}</a-radio>
        </a-radio-group>
      </div>

      <div class="app-cell" style="align-items: flex-start">
        <span class="app-cell-label">{{ t('aftersales.reason') }}</span>
        <a-textarea
          v-model="reason"
          :placeholder="t('aftersales.reasonPlaceholder')"
          :auto-size="{ minRows: 2, maxRows: 4 }"
          style="max-width: 260px"
        />
      </div>
    </div>

    <div class="app-card">
      <div class="app-section-title" style="margin-top: 0">
        {{ t('aftersales.selectItems') }}
      </div>
      <div class="text-muted" style="font-size: 12px; margin-bottom: 8px">
        {{ t('aftersales.selectItemsHint') }}
      </div>
      <div v-if="items.length === 0" class="app-empty" style="padding: 20px 0">
        {{ t('common.empty') }}
      </div>
      <div v-for="item in items" :key="item.id" class="apply-item" @click="toggleItem(item.id)">
        <a-checkbox :model-value="selectedIds.includes(item.id)" @click.stop />
        <div class="apply-item-main">
          <div class="apply-item-name">{{ item.skuName }}</div>
          <div class="text-muted" style="font-size: 12px">
            S${{ formatPrice(item.unitPrice) }} × {{ item.quantity }}
          </div>
        </div>
        <span class="text-price">S${{ formatPrice(item.totalPrice) }}</span>
      </div>
    </div>

    <div class="app-fixed-footer">
      <a-button class="app-primary-btn" type="primary" long :loading="submitting" @click="submit">
        {{ t('aftersales.submitApply') }}
      </a-button>
    </div>
  </FullPage>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { applyReturnOrder } from '@/api/aftersales'
import { getOrder } from '@/api/orders'
import type { OrderItemRecord } from '@/api/orders'
import FullPage from '@/components/FullPage.vue'

const { t } = useI18n()
const router = useRouter()

const orderNo = ref('')
const type = ref(1)
const reason = ref('')
const items = ref<OrderItemRecord[]>([])
const selectedIds = ref<number[]>([])
const loadingItems = ref(false)
const submitting = ref(false)

function formatPrice(value: string) {
  return Number(value).toFixed(2)
}

async function loadOrderItems() {
  if (!orderNo.value) {
    Message.warning(t('aftersales.mustSelectItems'))
    return
  }
  loadingItems.value = true
  try {
    const order = await getOrder(orderNo.value)
    items.value = order.items
    selectedIds.value = order.items.map((item) => item.id)
    Message.success(t('aftersales.loadOrderSuccess'))
  } catch {
    items.value = []
    selectedIds.value = []
  } finally {
    loadingItems.value = false
  }
}

function toggleItem(id: number) {
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter((value) => value !== id)
  } else {
    selectedIds.value = [...selectedIds.value, id]
  }
}

async function submit() {
  const selected = items.value.filter((item) => selectedIds.value.includes(item.id))
  if (!orderNo.value || selected.length === 0) {
    Message.warning(t('aftersales.mustSelectItems'))
    return
  }
  submitting.value = true
  try {
    await applyReturnOrder({
      orderNo: orderNo.value,
      type: type.value,
      reason: reason.value,
      items: selected.map((item) => ({
        orderItemId: item.id,
        skuId: item.skuId,
        quantity: item.quantity,
      })),
    })
    Message.success(t('aftersales.applySuccess'))
    router.replace({ name: 'aftersales' })
  } catch {
    Message.error(t('common.failed'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.apply-order-input {
  display: flex;
  gap: 8px;
  flex: 1;
  max-width: 280px;
}

.apply-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  cursor: pointer;
}

.apply-item + .apply-item {
  border-top: 1px solid var(--color-border-2);
}

.apply-item-main {
  flex: 1;
  min-width: 0;
}

.apply-item-name {
  font-size: 14px;
  color: var(--color-text-1);
  margin-bottom: 4px;
  word-break: break-all;
}
</style>

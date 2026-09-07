<template>
  <FullPage :title="t('checkout.title')">
    <div v-if="loading" class="app-empty">{{ t('common.loading') }}</div>

    <template v-else-if="createdOrder">
      <div class="app-card text-center">
        <IconCheckCircleFill class="result-icon" />
        <div class="result-title">{{ t('checkout.submitSuccess') }}</div>
        <div class="app-cell mt-12">
          <span class="app-cell-label">{{ t('checkout.orderNo') }}</span>
          <span class="app-cell-value">{{ createdOrder.orderNo }}</span>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('checkout.payAmount') }}</span>
          <span class="app-cell-value text-price">S${{ formatPrice(createdOrder.payAmount) }}</span>
        </div>
        <a-button class="app-primary-btn mt-12" type="primary" long @click="openPay">
          {{ t('checkout.goPay') }}
        </a-button>
        <a-button class="mt-12" long @click="goOrders">{{ t('checkout.viewOrders') }}</a-button>
      </div>
    </template>

    <template v-else-if="sku">
      <div class="app-card">
        <div class="app-section-title" style="margin-top: 0">{{ t('checkout.items') }}</div>
        <div class="flex-between">
          <div>
            <div class="checkout-item-name">{{ sku.name }}</div>
            <div class="text-muted">{{ sku.spec || sku.skuNo }}</div>
          </div>
          <div class="text-right">
            <div>S${{ formatPrice(sku.price) }} × {{ quantity }}</div>
            <div class="text-price">S${{ totalAmount }}</div>
          </div>
        </div>
      </div>

      <div class="app-card">
        <div class="app-cell">
          <span class="app-cell-label">{{ t('checkout.remark') }}</span>
          <a-textarea
            v-model="remark"
            :placeholder="t('checkout.remarkPlaceholder')"
            :auto-size="{ minRows: 2, maxRows: 4 }"
            style="max-width: 260px"
          />
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('checkout.totalAmount') }}</span>
          <span class="app-cell-value text-price">S${{ totalAmount }}</span>
        </div>
      </div>

      <div class="app-fixed-footer">
        <a-button class="app-primary-btn" type="primary" long :loading="submitting" @click="submit">
          {{ t('checkout.submitOrder') }}
        </a-button>
      </div>
    </template>
  </FullPage>

  <PayModal
    :visible="payVisible"
    :order-no="createdOrder?.orderNo ?? ''"
    @update:visible="payVisible = $event"
    @success="handlePaySuccess"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconCheckCircleFill } from '@arco-design/web-vue/es/icon'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { createOrder } from '@/api/orders'
import type { OrderDetail } from '@/api/orders'
import { getSku } from '@/api/skus'
import type { SkuRecord } from '@/api/skus'
import FullPage from '@/components/FullPage.vue'
import PayModal from '@/components/PayModal.vue'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const sku = ref<SkuRecord | null>(null)
const quantity = ref(1)
const remark = ref('')
const createdOrder = ref<OrderDetail | null>(null)

const payVisible = ref(false)

const totalAmount = computed(() => {
  if (!sku.value) {
    return '0.00'
  }
  return (Number(sku.value.price) * quantity.value).toFixed(2)
})

function formatPrice(value: string) {
  return Number(value).toFixed(2)
}

async function submit() {
  if (!sku.value) {
    return
  }
  submitting.value = true
  try {
    const order = await createOrder({
      merchantId: userStore.user?.merchantId,
      orderType: 1,
      remark: remark.value || undefined,
      items: [{ skuId: sku.value.id, quantity: quantity.value }],
    })
    createdOrder.value = order
    Message.success(t('checkout.submitSuccess'))
  } catch {
    Message.error(t('common.failed'))
  } finally {
    submitting.value = false
  }
}

function openPay() {
  if (createdOrder.value) {
    payVisible.value = true
  }
}

function handlePaySuccess() {
  if (createdOrder.value) {
    router.replace({ name: 'order-detail', params: { orderNo: createdOrder.value.orderNo } })
  }
}

function goOrders() {
  router.replace({ name: 'orders' })
}

onMounted(async () => {
  const skuId = route.query.skuId
  const qty = Number(route.query.quantity)
  if (!skuId) {
    router.replace({ name: 'home' })
    return
  }
  quantity.value = Number.isFinite(qty) && qty > 0 ? Math.min(Math.floor(qty), 99) : 1
  loading.value = true
  try {
    sku.value = await getSku(String(skuId))
  } catch {
    Message.error(t('common.failed'))
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.result-icon {
  font-size: 48px;
  color: rgb(var(--success-6));
}

.result-title {
  margin-top: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
}

.checkout-item-name {
  font-size: 14px;
  color: var(--color-text-1);
  margin-bottom: 4px;
}

.text-right {
  text-align: right;
}
</style>

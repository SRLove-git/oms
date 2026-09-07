<template>
  <div class="pay-result">
    <div class="pay-result-icon" :class="isSuccess ? 'is-success' : 'is-cancel'">
      {{ isSuccess ? '✓' : '×' }}
    </div>
    <div class="pay-result-title">
      {{ isSuccess ? t('payResult.success') : t('payResult.cancel') }}
    </div>
    <div v-if="paymentNo" class="pay-result-meta">
      {{ t('payResult.paymentNo') }}：{{ paymentNo }}
    </div>
    <div v-if="orderNo" class="pay-result-meta">
      {{ t('payResult.orderNo') }}：{{ orderNo }}
    </div>
    <div class="pay-result-actions">
      <a-button v-if="isSuccess && orderNo" type="primary" long @click="goOrder">
        {{ t('payResult.viewOrder') }}
      </a-button>
      <a-button long @click="goOrders">
        {{ t('payResult.backOrders') }}
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const isSuccess = computed(() => route.query.status === 'success')
const paymentNo = computed(() => (route.query.paymentNo as string) || undefined)
const orderNo = computed(() => (route.query.orderNo as string) || undefined)

function goOrder() {
  if (orderNo.value) {
    router.push({ name: 'order-detail', params: { orderNo: orderNo.value } })
  }
}

function goOrders() {
  router.push({ name: 'orders' })
}
</script>

<style scoped>
.pay-result {
  padding: 64px 24px;
  text-align: center;
}

.pay-result-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto 20px;
  border-radius: 50%;
  font-size: 40px;
  line-height: 72px;
  color: #fff;
}

.pay-result-icon.is-success {
  background: var(--color-success-6, #00b42a);
}

.pay-result-icon.is-cancel {
  background: var(--color-danger-6, #f53f3f);
}

.pay-result-title {
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
}

.pay-result-meta {
  color: var(--color-text-3, #86909c);
  margin-bottom: 4px;
}

.pay-result-actions {
  margin-top: 28px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>

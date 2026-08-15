<template>
  <a-modal
    :visible="visible"
    :title="t('payment.title')"
    :ok-text="t('orders.simulatePay')"
    :cancel-text="t('common.cancel')"
    :ok-button-props="{ disabled: !payInfo }"
    @cancel="handleCancel"
    @ok="handleOk"
  >
    <div v-if="loading">{{ t('common.loading') }}</div>
    <template v-else>
      <div>{{ t('payment.content', { paymentNo: payInfo?.paymentNo ?? '-' }) }}</div>
      <div class="mt-8">
        {{ t('payment.amount') }}：
        <span class="text-price">S${{ payInfo ? formatPrice(payInfo.amount) : '-' }}</span>
      </div>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'

import { callbackMock, payOrder } from '@/api/orders'

const props = defineProps<{
  visible: boolean
  orderNo: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}>()

const { t } = useI18n()

const loading = ref(false)
const payInfo = ref<{ paymentNo: string; amount: string } | null>(null)

function formatPrice(value: string) {
  return Number(value).toFixed(2)
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) {
      return
    }
    loading.value = true
    payInfo.value = null
    try {
      const result = await payOrder(props.orderNo)
      payInfo.value = { paymentNo: result.paymentNo, amount: result.amount }
    } catch {
      Message.error(t('orders.payFailed'))
    } finally {
      loading.value = false
    }
  },
)

function handleCancel() {
  emit('update:visible', false)
}

async function handleOk() {
  if (!payInfo.value) {
    return
  }
  try {
    await callbackMock({
      paymentNo: payInfo.value.paymentNo,
      channelTxnNo: `TXN${Date.now()}`,
      amount: payInfo.value.amount,
      status: 'SUCCESS',
    })
    Message.success(t('orders.paySuccess'))
    emit('update:visible', false)
    emit('success')
  } catch {
    Message.error(t('orders.payFailed'))
  }
}
</script>

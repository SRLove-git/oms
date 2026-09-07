<template>
  <a-modal
    :visible="visible"
    :title="t('payment.title')"
    :ok-text="t('payment.payNow')"
    :ok-button-props="{ loading }"
    :cancel-text="t('common.cancel')"
    @cancel="handleCancel"
    @ok="handleOk"
  >
    <a-radio-group v-model="channel" type="button" class="pay-channel">
      <a-radio value="mock">{{ t('payment.channelMock') }}</a-radio>
      <a-radio value="visa">{{ t('payment.channelVisa') }}</a-radio>
      <a-radio value="mastercard">{{ t('payment.channelMastercard') }}</a-radio>
    </a-radio-group>
  </a-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
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
const channel = ref('mock')
const loading = ref(false)

function handleCancel() {
  emit('update:visible', false)
}

async function handleOk() {
  loading.value = true
  try {
    const result = await payOrder(props.orderNo, channel.value)
    if (channel.value !== 'mock' && result.payUrl) {
      window.location.href = result.payUrl
      return
    }
    await callbackMock({
      paymentNo: result.paymentNo,
      channelTxnNo: `TXN${Date.now()}`,
      amount: result.amount,
      status: 'SUCCESS',
    })
    Message.success(t('orders.paySuccess'))
    emit('update:visible', false)
    emit('success')
  } catch {
    Message.error(t('orders.payFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.pay-channel {
  width: 100%;
}
</style>

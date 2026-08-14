<template>
  <div>
    <a-button class="mb-12" type="primary" long @click="goApply">
      {{ t('aftersales.apply') }}
    </a-button>

    <div v-if="loading && list.length === 0" class="app-empty">{{ t('common.loading') }}</div>
    <div v-else-if="list.length === 0" class="app-empty">{{ t('common.empty') }}</div>

    <template v-else>
      <div v-for="item in list" :key="item.id" class="app-list-item">
        <div class="app-list-item-header">
          <span class="app-list-item-title">{{ item.returnNo }}</span>
          <a-tag :color="afterStatusColor(item.status)" size="small">
            {{ afterStatusName(item.status) }}
          </a-tag>
        </div>
        <div class="app-list-item-row">
          <span>{{ t('aftersales.orderNo') }}</span>
          <span>{{ item.orderNo }}</span>
        </div>
        <div class="app-list-item-row">
          <span>{{ t('aftersales.type') }}</span>
          <span>{{ afterTypeName(item.type) }}</span>
        </div>
        <div class="app-list-item-row">
          <span>{{ t('aftersales.reason') }}</span>
          <span>{{ item.reason || '-' }}</span>
        </div>
        <div class="app-list-item-row">
          <span>{{ t('aftersales.amount') }}</span>
          <span class="text-price">¥{{ formatPrice(item.totalAmount) }}</span>
        </div>
        <div class="app-list-item-row">
          <span>{{ t('aftersales.applyTime') }}</span>
          <span>{{ item.createdAt }}</span>
        </div>
        <div v-if="item.status === 1 || item.status === 2 || item.status === 4" class="app-list-item-actions">
          <a-button status="danger" size="small" @click="handleCancel(item)">
            {{ t('aftersales.cancelApply') }}
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { cancelReturnOrder, pageReturnOrders } from '@/api/aftersales'
import type { ReturnOrderSummary } from '@/api/aftersales'

const { t } = useI18n()
const router = useRouter()

const list = ref<ReturnOrderSummary[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

function afterTypeName(type: number) {
  return t(`afterType.${type}` as never) || String(type)
}

function afterStatusName(status: number) {
  return t(`afterStatus.${status}` as never) || String(status)
}

function afterStatusColor(status: number) {
  if (status === 6) {
    return 'green'
  }
  if (status === 3 || status === 7) {
    return 'gray'
  }
  if (status === 5) {
    return 'orange'
  }
  return 'arcoblue'
}

function formatPrice(value: string) {
  return Number(value).toFixed(2)
}

async function load(reset: boolean) {
  loading.value = true
  try {
    const res = await pageReturnOrders({
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

function goApply() {
  router.push({ name: 'aftersales-apply' })
}

function handleCancel(item: ReturnOrderSummary) {
  Modal.confirm({
    title: t('aftersales.cancelApply'),
    content: t('aftersales.cancelConfirm'),
    okButtonProps: { status: 'danger' },
    onOk: async () => {
      await cancelReturnOrder(item.returnNo)
      Message.success(t('aftersales.cancelSuccess'))
      load(true)
    },
  })
}

onMounted(() => {
  load(true)
})
</script>

<template>
  <div>
    <a-input-search
      v-model="keyword"
      :placeholder="t('home.searchPlaceholder')"
      search-button
      allow-clear
      size="large"
      class="mb-12"
      @search="handleSearch"
    />

    <div v-if="loading && list.length === 0" class="app-empty">{{ t('common.loading') }}</div>
    <div v-else-if="list.length === 0" class="app-empty">{{ t('home.empty') }}</div>

    <div v-else class="sku-grid">
      <div v-for="sku in list" :key="sku.id" class="sku-card" @click="goDetail(sku.id)">
        <div class="sku-card-body">
          <div class="sku-card-name">{{ sku.name }}</div>
          <div class="sku-card-spec">{{ sku.spec || sku.skuNo }}</div>
          <div class="sku-card-footer">
            <span class="sku-card-price">S${{ formatPrice(sku.price) }}</span>
            <a-tag
              class="sku-card-status"
              :color="sku.status === 1 ? 'green' : 'gray'"
              size="small"
            >
              {{ sku.status === 1 ? t('home.onSale') : t('home.offShelf') }}
            </a-tag>
          </div>
        </div>
      </div>
    </div>

    <div class="text-center mt-12">
      <a-button v-if="list.length > 0 && list.length < total" :loading="loading" @click="loadMore">
        {{ t('common.loadMore') }}
      </a-button>
      <span v-else-if="list.length > 0" class="text-muted">{{ t('common.noMore') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { pageSkus } from '@/api/skus'
import type { SkuRecord } from '@/api/skus'

const { t } = useI18n()
const router = useRouter()

const keyword = ref('')
const list = ref<SkuRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const loading = ref(false)

function formatPrice(value: string) {
  return Number(value).toFixed(2)
}

async function load(reset: boolean) {
  loading.value = true
  try {
    const res = await pageSkus({
      keyword: keyword.value,
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

function handleSearch() {
  load(true)
}

function loadMore() {
  page.value += 1
  load(false)
}

function goDetail(id: number) {
  router.push({ name: 'sku-detail', params: { id: String(id) } })
}

onMounted(() => {
  load(true)
})
</script>

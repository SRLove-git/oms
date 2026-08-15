<template>
  <FullPage :title="t('skuDetail.title')">
    <div v-if="!sku" class="app-empty">{{ t('common.loading') }}</div>

    <template v-else>
      <div class="app-card">
        <div class="sku-detail-name">{{ sku.name }}</div>
        <div class="sku-detail-spec text-muted">{{ sku.spec || '-' }}</div>
        <div class="sku-detail-price">S${{ formatPrice(sku.price) }}</div>
        <div class="mt-12">
          <a-tag :color="sku.status === 1 ? 'green' : 'gray'" size="small">
            {{ sku.status === 1 ? t('home.onSale') : t('home.offShelf') }}
          </a-tag>
        </div>
      </div>

      <div class="app-card">
        <div class="app-cell">
          <span class="app-cell-label">{{ t('skuDetail.skuNo') }}</span>
          <span class="app-cell-value">{{ sku.skuNo }}</span>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('skuDetail.spec') }}</span>
          <span class="app-cell-value">{{ sku.spec || '-' }}</span>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('skuDetail.registrationNo') }}</span>
          <span class="app-cell-value">{{ sku.registrationNo || '-' }}</span>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('skuDetail.udi') }}</span>
          <span class="app-cell-value">{{ sku.udi || '-' }}</span>
        </div>
        <div class="app-cell">
          <span class="app-cell-label">{{ t('skuDetail.barcode') }}</span>
          <span class="app-cell-value">{{ sku.barcode || '-' }}</span>
        </div>
      </div>

      <div class="app-card flex-between">
        <span class="app-cell-label">{{ t('skuDetail.quantity') }}</span>
        <a-input-number
          v-model="quantity"
          :min="1"
          :max="99"
          :precision="0"
          size="large"
          style="width: 140px"
        />
      </div>

      <div class="app-fixed-footer">
        <a-button class="app-primary-btn" type="primary" long @click="buyNow">
          {{ t('skuDetail.buyNow') }}
        </a-button>
      </div>
    </template>
  </FullPage>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { getSku } from '@/api/skus'
import type { SkuRecord } from '@/api/skus'
import FullPage from '@/components/FullPage.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const sku = ref<SkuRecord | null>(null)
const quantity = ref(1)

function formatPrice(value: string) {
  return Number(value).toFixed(2)
}

function buyNow() {
  if (!sku.value) {
    return
  }
  router.push({
    name: 'checkout',
    query: { skuId: String(sku.value.id), quantity: String(quantity.value) },
  })
}

onMounted(async () => {
  try {
    sku.value = await getSku(String(route.params.id))
  } catch {
    Message.error(t('common.failed'))
  }
})
</script>

<style scoped>
.sku-detail-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
  line-height: 1.4;
}

.sku-detail-spec {
  margin-top: 4px;
  font-size: 13px;
}

.sku-detail-price {
  margin-top: 12px;
  font-size: 22px;
  font-weight: 600;
  color: rgb(var(--danger-6));
}
</style>

<template>
  <div class="app-shell">
    <header class="app-header">
      <button class="app-header-back" type="button" @click="handleBack">
        <IconArrowLeft />
        {{ t('common.back') }}
      </button>
      <div class="app-header-title">{{ title }}</div>
      <div class="app-header-slot"></div>
    </header>
    <main class="app-page app-page--full">
      <slot />
    </main>
  </div>
</template>

<script setup lang="ts">
import { IconArrowLeft } from '@arco-design/web-vue/es/icon'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

defineProps<{ title: string }>()

const { t } = useI18n()
const router = useRouter()

function handleBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'home' })
  }
}
</script>

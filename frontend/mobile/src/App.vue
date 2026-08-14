<template>
  <a-config-provider :locale="arcoLocale">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'

import zhCn from '@arco-design/web-vue/es/locale/lang/zh-cn'
import enUs from '@arco-design/web-vue/es/locale/lang/en-us'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

const arcoLocale = computed(() => (appStore.locale === 'zh-CN' ? zhCn : enUs))
</script>

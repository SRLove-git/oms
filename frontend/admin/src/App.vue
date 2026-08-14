<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import enUSArco from '@arco-design/web-vue/es/locale/lang/en-us'
import zhCNArco from '@arco-design/web-vue/es/locale/lang/zh-cn'

const { locale } = useI18n()

const arcoLocale = computed(() => (locale.value === 'en-US' ? enUSArco : zhCNArco))
</script>

<template>
  <a-config-provider :locale="arcoLocale">
    <router-view />
  </a-config-provider>
</template>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="app-header-slot"></div>
      <div class="app-header-title">{{ pageTitle }}</div>
      <div class="app-header-slot"></div>
    </header>

    <main class="app-page">
      <router-view />
    </main>

    <nav class="app-tabbar">
      <router-link
        v-for="item in tabs"
        :key="item.key"
        :to="{ name: item.key }"
        class="app-tabbar-item"
        :class="{ 'app-tabbar-item--active': activeTab === item.key }"
      >
        <span class="app-tabbar-icon">
          <component :is="item.icon" />
        </span>
        <span>{{ t(item.label) }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { IconHome, IconList, IconSafe, IconUser } from '@arco-design/web-vue/es/icon'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'

const { t } = useI18n()
const route = useRoute()

const tabs = [
  { key: 'home', label: 'tabbar.home', icon: IconHome },
  { key: 'orders', label: 'tabbar.orders', icon: IconList },
  { key: 'aftersales', label: 'tabbar.aftersales', icon: IconSafe },
  { key: 'profile', label: 'tabbar.profile', icon: IconUser },
]

const activeTab = computed(() => (route.meta.tab as string | undefined) ?? route.name)

const pageTitle = computed(() => {
  const key = route.meta.title as string | undefined
  return key ? t(key) : t('common.appName')
})
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { i18n, type Locale } from '@/i18n'
import { updateDocumentTitle } from '@/router/title'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const headerTitle = computed(() => t(String(route.meta.title ?? 'app.name')))

type MenuKey = string | number | Record<string, unknown> | undefined

function onMenuClick(key: MenuKey) {
  const path = typeof key === 'string' ? key : ''
  if (path !== route.path) {
    router.push(path)
  }
}

function onUserAction(key: MenuKey) {
  if (key === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}

function onLanguageSelect(key: MenuKey) {
  const value = typeof key === 'string' ? key : ''
  if (value === 'zh-CN' || value === 'en-US') {
    appStore.setLocale(value as Locale)
    i18n.global.locale.value = value
    updateDocumentTitle(route)
  }
}
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-sider class="basic-sider" :collapsed="appStore.collapsed" collapsible :width="220">
      <div class="logo">{{ appStore.collapsed ? t('app.shortName') : t('app.name') }}</div>
      <a-menu :selected-keys="[route.path]" @menu-item-click="onMenuClick">
        <a-menu-item key="/home">
          <template #icon><icon-home /></template>
          {{ t('menu.home') }}
        </a-menu-item>
        <a-menu-item key="/orders">{{ t('menu.orders') }}</a-menu-item>
        <a-menu-item key="/products">{{ t('menu.products') }}</a-menu-item>
        <a-menu-item key="/inventories">{{ t('menu.inventories') }}</a-menu-item>
        <a-menu-item key="/payments">{{ t('menu.payments') }}</a-menu-item>
        <a-menu-item key="/merchants">{{ t('menu.merchants') }}</a-menu-item>
        <a-menu-item key="/qualifications">{{ t('menu.qualifications') }}</a-menu-item>
        <a-menu-item key="/users">{{ t('menu.users') }}</a-menu-item>
        <a-menu-item key="/audit-logs">{{ t('menu.auditLogs') }}</a-menu-item>
        <a-menu-item key="/after-sales">{{ t('menu.afterSales') }}</a-menu-item>
        <a-menu-item key="/reconciliation">{{ t('menu.reconciliation') }}</a-menu-item>
        <a-menu-item key="/logistics">{{ t('menu.logistics') }}</a-menu-item>
        <a-menu-item key="/notifications">{{ t('menu.notifications') }}</a-menu-item>
        <a-sub-menu key="reports">
          <template #title>{{ t('menu.reports') }}</template>
          <a-menu-item key="/reports/sales">{{ t('menu.reportsSales') }}</a-menu-item>
          <a-menu-item key="/reports/inventory">{{ t('menu.reportsInventory') }}</a-menu-item>
          <a-menu-item key="/reports/payments">{{ t('menu.reportsPayments') }}</a-menu-item>
          <a-menu-item key="/reports/aftersales">{{ t('menu.reportsAftersales') }}</a-menu-item>
        </a-sub-menu>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="basic-header">
        <a-button type="text" class="collapse-btn" @click="appStore.toggleCollapsed()">
          <icon-menu-unfold v-if="appStore.collapsed" />
          <icon-menu-fold v-else />
        </a-button>
        <span class="header-title">{{ headerTitle }}</span>
        <a-space class="header-actions">
          <a-dropdown @select="onLanguageSelect">
            <a-button type="text" :aria-label="t('common.language')">
              <template #icon><icon-language /></template>
              {{ appStore.locale === 'zh-CN' ? '简体中文' : 'English' }}
            </a-button>
            <template #content>
              <a-doption value="zh-CN">简体中文</a-doption>
              <a-doption value="en-US">English</a-doption>
            </template>
          </a-dropdown>
          <a-button
            type="text"
            class="theme-btn"
            :aria-label="appStore.theme === 'dark' ? t('common.lightMode') : t('common.darkMode')"
            @click="appStore.toggleTheme()"
          >
            <icon-sun-fill v-if="appStore.theme === 'dark'" />
            <icon-moon-fill v-else />
          </a-button>
          <a-dropdown @select="onUserAction">
            <a-button type="text">
              {{ userStore.user?.realName ?? userStore.user?.username }}
            </a-button>
            <template #content>
              <a-doption value="logout">{{ t('common.logout') }}</a-doption>
            </template>
          </a-dropdown>
        </a-space>
      </a-layout-header>

      <a-layout-content class="basic-content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<style scoped>
.basic-layout {
  min-height: 100vh;
}

.basic-sider {
  background-color: var(--color-menu-light-bg, #fff);
}

.logo {
  height: 56px;
  line-height: 56px;
  padding: 0 20px;
  font-size: 16px;
  font-weight: 600;
  overflow: hidden;
  white-space: nowrap;
}

.basic-header {
  display: flex;
  align-items: center;
  height: 56px;
  background-color: var(--color-bg-2, #fff);
  border-bottom: 1px solid var(--color-border-2, #eee);
}

.collapse-btn {
  font-size: 18px;
}

.theme-btn {
  font-size: 16px;
}

.header-title {
  font-size: 15px;
  font-weight: 500;
}

.header-actions {
  margin-left: auto;
}

.basic-content {
  padding: 16px;
  background-color: var(--color-fill-1, #f7f8fa);
}
</style>

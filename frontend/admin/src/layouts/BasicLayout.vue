<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

import { useAppStore } from '@/stores/app'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()

function onMenuClick(key: string) {
  if (key !== route.path) {
    router.push(key)
  }
}
</script>

<template>
  <a-layout class="basic-layout">
    <a-layout-sider class="basic-sider" :collapsed="appStore.collapsed" collapsible :width="220">
      <div class="logo">{{ appStore.collapsed ? 'OMS' : 'OMS 管理端' }}</div>
      <a-menu :selected-keys="[route.path]" @menu-item-click="onMenuClick">
        <a-menu-item key="/home">
          <template #icon><icon-home /></template>
          首页
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="basic-header">
        <a-button type="text" class="collapse-btn" @click="appStore.toggleCollapsed()">
          <icon-menu-unfold v-if="appStore.collapsed" />
          <icon-menu-fold v-else />
        </a-button>
        <span class="header-title">{{ route.meta.title ?? 'OMS 管理端' }}</span>
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

.header-title {
  font-size: 15px;
  font-weight: 500;
}

.basic-content {
  padding: 16px;
  background-color: var(--color-fill-1, #f7f8fa);
}
</style>

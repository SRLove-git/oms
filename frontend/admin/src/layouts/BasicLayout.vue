<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

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
        <a-menu-item key="/orders">订单管理</a-menu-item>
        <a-menu-item key="/products">商品管理</a-menu-item>
        <a-menu-item key="/inventories">库存管理</a-menu-item>
        <a-menu-item key="/payments">支付记录</a-menu-item>
        <a-menu-item key="/merchants">商户管理</a-menu-item>
        <a-menu-item key="/qualifications">资质管理</a-menu-item>
        <a-menu-item key="/users">用户管理</a-menu-item>
        <a-menu-item key="/audit-logs">审计日志</a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header class="basic-header">
        <a-button type="text" class="collapse-btn" @click="appStore.toggleCollapsed()">
          <icon-menu-unfold v-if="appStore.collapsed" />
          <icon-menu-fold v-else />
        </a-button>
        <span class="header-title">{{ route.meta.title ?? 'OMS 管理端' }}</span>
        <a-space class="header-actions">
          <a-dropdown @select="onUserAction">
            <a-button type="text">
              {{ userStore.user?.realName ?? userStore.user?.username }}
            </a-button>
            <template #content>
              <a-doption value="logout">退出登录</a-doption>
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

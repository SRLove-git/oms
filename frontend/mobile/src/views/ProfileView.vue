<template>
  <div>
    <div class="profile-card app-card">
      <div class="profile-avatar">
        <IconUser />
      </div>
      <div class="profile-info">
        <div class="profile-name">{{ userStore.displayName }}</div>
        <div class="text-muted">@{{ userStore.user?.username }}</div>
      </div>
    </div>

    <div class="app-card">
      <div class="app-section-title" style="margin-top: 0">{{ t('profile.account') }}</div>
      <div class="app-cell">
        <span class="app-cell-label">{{ t('profile.username') }}</span>
        <span class="app-cell-value">{{ userStore.user?.username ?? '-' }}</span>
      </div>
      <div class="app-cell">
        <span class="app-cell-label">{{ t('profile.userType') }}</span>
        <span class="app-cell-value">
          {{ userStore.user?.userType === 1 ? t('profile.userTypeAdmin') : t('profile.userTypeMerchant') }}
        </span>
      </div>
      <div class="app-cell">
        <span class="app-cell-label">{{ t('profile.merchantId') }}</span>
        <span class="app-cell-value">{{ userStore.user?.merchantId ?? '-' }}</span>
      </div>
    </div>

    <div class="app-card">
      <div class="app-section-title" style="margin-top: 0">{{ t('profile.settings') }}</div>
      <div class="app-cell">
        <span class="app-cell-label">{{ t('profile.language') }}</span>
        <a-radio-group v-model="locale" type="button" size="small" @change="handleLocaleChange">
          <a-radio value="zh-CN">中文</a-radio>
          <a-radio value="en-US">English</a-radio>
        </a-radio-group>
      </div>
      <div class="app-cell">
        <span class="app-cell-label">{{ t('profile.theme') }}</span>
        <a-radio-group v-model="theme" type="button" size="small" @change="handleThemeChange">
          <a-radio value="light">{{ t('profile.themeLight') }}</a-radio>
          <a-radio value="dark">{{ t('profile.themeDark') }}</a-radio>
        </a-radio-group>
      </div>
    </div>

    <a-button class="mt-12" status="danger" long @click="handleLogout">
      {{ t('profile.logout') }}
    </a-button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { IconUser } from '@arco-design/web-vue/es/icon'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { useAppStore } from '@/stores/app'
import type { LocaleCode, ThemeMode } from '@/stores/app'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

const locale = ref<LocaleCode>(appStore.locale)
const theme = ref<ThemeMode>(appStore.theme)

function handleLocaleChange(value: string | number | boolean) {
  appStore.setLocale(value as LocaleCode)
}

function handleThemeChange(value: string | number | boolean) {
  appStore.setTheme(value as ThemeMode)
}

function handleLogout() {
  Modal.confirm({
    title: t('profile.logout'),
    content: t('profile.logoutConfirm'),
    onOk: () => {
      userStore.logout()
      Message.success(t('profile.logoutSuccess'))
      router.replace({ name: 'login' })
    },
  })
}
</script>

<style scoped>
.profile-card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: var(--color-fill-2);
  color: var(--color-text-3);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  flex-shrink: 0;
}

.profile-name {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-1);
  margin-bottom: 4px;
}
</style>

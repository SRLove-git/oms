<template>
  <div class="login-page">
    <div class="login-brand">
      <IconSafe class="login-logo" />
      <h1 class="login-title">{{ t('login.title') }}</h1>
      <p class="login-subtitle">{{ t('login.subtitle') }}</p>
    </div>

    <div class="app-card login-card">
      <a-form layout="vertical" :model="form" @submit="handleLogin">
        <a-form-item
          :label="t('login.username')"
          field="username"
          :rules="[{ required: true, message: t('login.requireUsername') }]"
        >
          <a-input
            v-model="form.username"
            :placeholder="t('login.usernamePlaceholder')"
            allow-clear
            size="large"
          />
        </a-form-item>
        <a-form-item
          :label="t('login.password')"
          field="password"
          :rules="[{ required: true, message: t('login.requirePassword') }]"
        >
          <a-input-password
            v-model="form.password"
            :placeholder="t('login.passwordPlaceholder')"
            allow-clear
            size="large"
          />
        </a-form-item>
        <a-button class="app-primary-btn" type="primary" html-type="submit" long :loading="loading">
          {{ t('login.loginBtn') }}
        </a-button>
      </a-form>
      <p class="login-demo text-muted">{{ t('login.demoHint') }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { IconSafe } from '@arco-design/web-vue/es/icon'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const form = reactive({
  username: 'merchant',
  password: 'merchant123',
})
const loading = ref(false)

async function handleLogin() {
  if (!form.username || !form.password) {
    Message.warning(t('login.requireUsername'))
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    Message.success(t('login.loginSuccess'))
    router.push({ name: 'home' })
  } catch {
    Message.error(t('login.loginFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 24px;
  max-width: var(--app-max-width);
  margin: 0 auto;
}

.login-brand {
  text-align: center;
  margin-bottom: 28px;
}

.login-logo {
  font-size: 48px;
  color: rgb(var(--primary-6));
}

.login-title {
  margin: 12px 0 4px;
  font-size: 22px;
  color: var(--color-text-1);
}

.login-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-3);
}

.login-demo {
  margin: 16px 0 0;
  font-size: 12px;
  text-align: center;
}
</style>

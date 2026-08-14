<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { useUserStore } from '@/stores/user'

const { t } = useI18n()

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: 'admin123',
})

async function handleLogin() {
  if (!form.username || !form.password) {
    Message.warning(t('login.emptyWarning'))
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    Message.success(t('login.success'))
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <a-card class="login-card" :title="t('login.title')">
      <a-form :model="form" layout="vertical" @submit="handleLogin">
        <a-form-item :label="t('login.username')">
          <a-input v-model="form.username" :placeholder="t('login.usernamePlaceholder')" />
        </a-form-item>
        <a-form-item :label="t('login.password')">
          <a-input-password v-model="form.password" :placeholder="t('login.passwordPlaceholder')" />
        </a-form-item>
        <a-button type="primary" html-type="submit" long :loading="loading">
          {{ t('login.submit') }}
        </a-button>
      </a-form>
    </a-card>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background-color: var(--color-fill-1, #f7f8fa);
}

.login-card {
  width: 360px;
}
</style>

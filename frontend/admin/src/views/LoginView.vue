<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const loading = ref(false)
const form = reactive({
  username: '',
  password: '',
})

async function handleLogin() {
  if (!form.username || !form.password) {
    Message.warning('请输入账号和密码')
    return
  }
  loading.value = true
  // TODO: 接入 user-service 登录接口后替换为真实调用
  setTimeout(() => {
    loading.value = false
    Message.info('登录接口待接入')
    router.push('/')
  }, 500)
}
</script>

<template>
  <div class="login-page">
    <a-card class="login-card" title="OMS 管理端登录">
      <a-form :model="form" layout="vertical" @submit="handleLogin">
        <a-form-item label="账号">
          <a-input v-model="form.username" placeholder="请输入账号" />
        </a-form-item>
        <a-form-item label="密码">
          <a-input-password v-model="form.password" placeholder="请输入密码" />
        </a-form-item>
        <a-button type="primary" html-type="submit" long :loading="loading"> 登 录 </a-button>
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

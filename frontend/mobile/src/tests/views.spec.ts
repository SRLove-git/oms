import { describe, it, expect, beforeAll } from 'vitest'
import { mount } from '@vue/test-utils'
import ArcoVue from '@arco-design/web-vue'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'

import i18n from '@/i18n'
import LoginView from '@/views/LoginView.vue'

const router = createRouter({
  history: createMemoryHistory(),
  routes: [{ path: '/login', name: 'login', component: LoginView }],
})

function mountLogin() {
  return mount(LoginView, {
    global: {
      plugins: [createPinia(), i18n, router, ArcoVue],
    },
  })
}

describe('LoginView', () => {
  beforeAll(() => {
    localStorage.clear()
    i18n.global.locale.value = 'zh-CN'
  })

  it('renders the login form with i18n labels', async () => {
    const wrapper = mountLogin()
    const text = wrapper.text()
    expect(text).toContain('商家登录')
    expect(text).toContain('账号')
    expect(text).toContain('密码')
    expect(text).toContain('merchant / merchant123')
    wrapper.unmount()
  })

  it('renders in English when locale is en-US', async () => {
    i18n.global.locale.value = 'en-US'
    const wrapper = mountLogin()
    const text = wrapper.text()
    expect(text).toContain('Merchant Login')
    expect(text).toContain('Demo account')
    wrapper.unmount()
    i18n.global.locale.value = 'zh-CN'
  })
})

import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'

import ArcoVue from '@arco-design/web-vue'
import { login as loginApi } from '@/api/auth'
import i18n from '@/i18n'
import LoginView from '@/views/LoginView.vue'

vi.mock('@/api/auth', () => ({
  login: vi.fn().mockResolvedValue({
    token: 'test-token',
    user: { id: 1, username: 'admin', realName: 'Admin', userType: 1, status: 1 },
  }),
  fetchMe: vi.fn(),
}))

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
}

describe('LoginView', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    i18n.global.locale.value = 'zh-CN'
    vi.mocked(loginApi).mockClear()
  })

  async function mountView() {
    const router = createTestRouter()
    const wrapper = mount(LoginView, {
      global: {
        plugins: [i18n, ArcoVue, router],
      },
    })
    await router.isReady()
    return { wrapper, router }
  }

  it('渲染账号/密码输入框与登录按钮', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.text()).toContain('OMS 管理端登录')
    expect(wrapper.text()).toContain('账号')
    expect(wrapper.text()).toContain('密码')
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    expect(wrapper.find('input[type="password"]').exists()).toBe(true)
    expect(wrapper.find('button[type="submit"]').exists()).toBe(true)
  })

  it('切换语言后表单文案随之变化', async () => {
    const { wrapper } = await mountView()
    expect(wrapper.text()).toContain('OMS 管理端登录')
    expect(wrapper.text()).toContain('账号')

    i18n.global.locale.value = 'en-US'
    await flushPromises()
    expect(wrapper.text()).toContain('OMS Admin Login')
    expect(wrapper.text()).toContain('Username')
    expect(wrapper.text()).toContain('Password')
  })

  it('提交表单时调用登录接口并跳转首页', async () => {
    const { wrapper, router } = await mountView()
    const push = vi.spyOn(router, 'push')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(vi.mocked(loginApi)).toHaveBeenCalledTimes(1)
    expect(vi.mocked(loginApi)).toHaveBeenCalledWith('admin', 'admin123')
    expect(push).toHaveBeenCalledWith('/')
  })

  it('账号或密码为空时不调用登录接口', async () => {
    const { wrapper } = await mountView()

    await wrapper.find('input[type="text"]').setValue('')
    await wrapper.find('input[type="password"]').setValue('')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(vi.mocked(loginApi)).not.toHaveBeenCalled()
  })
})

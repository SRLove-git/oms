import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import { useAppStore } from '@/stores/app'

describe('app store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('初始化时从 localStorage 读取 locale 与 theme', () => {
    localStorage.setItem('oms-locale', 'en-US')
    localStorage.setItem('oms-theme', 'dark')
    const store = useAppStore()
    expect(store.locale).toBe('en-US')
    expect(store.theme).toBe('dark')
  })

  it('未持久化时回退到 zh-CN 与 light', () => {
    const store = useAppStore()
    expect(store.locale).toBe('zh-CN')
    expect(store.theme).toBe('light')
  })

  it('setLocale 切换语言并持久化到 localStorage', () => {
    const store = useAppStore()
    expect(store.locale).toBe('zh-CN')
    store.setLocale('en-US')
    expect(store.locale).toBe('en-US')
    expect(localStorage.getItem('oms-locale')).toBe('en-US')
    store.setLocale('zh-CN')
    expect(localStorage.getItem('oms-locale')).toBe('zh-CN')
  })

  it('setTheme 切换主题并持久化，同时同步 body 的 arco-theme 属性', () => {
    const store = useAppStore()
    store.setTheme('dark')
    expect(store.theme).toBe('dark')
    expect(localStorage.getItem('oms-theme')).toBe('dark')
    expect(document.body.getAttribute('arco-theme')).toBe('dark')
    store.setTheme('light')
    expect(store.theme).toBe('light')
    expect(localStorage.getItem('oms-theme')).toBe('light')
    expect(document.body.hasAttribute('arco-theme')).toBe(false)
  })

  it('toggleTheme 在亮暗之间切换', () => {
    const store = useAppStore()
    store.toggleTheme()
    expect(store.theme).toBe('dark')
    expect(localStorage.getItem('oms-theme')).toBe('dark')
    store.toggleTheme()
    expect(store.theme).toBe('light')
    expect(localStorage.getItem('oms-theme')).toBe('light')
  })
})

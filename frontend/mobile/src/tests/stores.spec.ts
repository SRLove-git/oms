import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

import { LOCALE_KEY, THEME_KEY, useAppStore } from '@/stores/app'

describe('app store (locale / theme persistence)', () => {
  beforeEach(() => {
    localStorage.clear()
    document.body.removeAttribute('arco-theme')
    setActivePinia(createPinia())
  })

  it('persists locale to localStorage', () => {
    const store = useAppStore()
    expect(store.locale).toBe('zh-CN')

    store.setLocale('en-US')
    expect(store.locale).toBe('en-US')
    expect(localStorage.getItem(LOCALE_KEY)).toBe('en-US')

    store.setLocale('zh-CN')
    expect(store.locale).toBe('zh-CN')
    expect(localStorage.getItem(LOCALE_KEY)).toBe('zh-CN')
  })

  it('persists theme and applies arco-theme attribute on body', () => {
    const store = useAppStore()
    expect(store.theme).toBe('light')

    store.setTheme('dark')
    expect(store.theme).toBe('dark')
    expect(localStorage.getItem(THEME_KEY)).toBe('dark')
    expect(document.body.getAttribute('arco-theme')).toBe('dark')

    store.toggleTheme()
    expect(store.theme).toBe('light')
    expect(document.body.getAttribute('arco-theme')).toBeNull()
  })

  it('restores persisted theme and locale on init', () => {
    localStorage.setItem(THEME_KEY, 'dark')
    localStorage.setItem(LOCALE_KEY, 'en-US')

    const store = useAppStore()
    store.init()

    expect(store.theme).toBe('dark')
    expect(store.locale).toBe('en-US')
    expect(document.body.getAttribute('arco-theme')).toBe('dark')
  })
})

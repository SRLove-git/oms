import { beforeEach, describe, expect, it } from 'vitest'

import { appStore } from '@/stores/app'

describe('appStore', () => {
  beforeEach(() => {
    localStorage.clear()
    appStore.init()
  })

  it('defaults to zh-CN and light theme', () => {
    expect(appStore.getSnapshot()).toEqual({ locale: 'zh-CN', theme: 'light' })
  })

  it('setLocale updates state and persists to localStorage', () => {
    appStore.setLocale('en-US')
    expect(appStore.getSnapshot().locale).toBe('en-US')
    expect(localStorage.getItem('oms-locale')).toBe('en-US')

    // a fresh init re-reads the persisted locale
    appStore.init()
    expect(appStore.getSnapshot().locale).toBe('en-US')
  })

  it('setTheme / toggleTheme flip and persist to localStorage', () => {
    appStore.setTheme('dark')
    expect(appStore.getSnapshot().theme).toBe('dark')
    expect(localStorage.getItem('oms-theme')).toBe('dark')

    appStore.toggleTheme()
    expect(appStore.getSnapshot().theme).toBe('light')
    expect(localStorage.getItem('oms-theme')).toBe('light')

    appStore.toggleTheme()
    expect(appStore.getSnapshot().theme).toBe('dark')
  })
})

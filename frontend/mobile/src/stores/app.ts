import { defineStore } from 'pinia'

import { setI18nLocale } from '@/i18n'

export type ThemeMode = 'light' | 'dark'
export type LocaleCode = 'zh-CN' | 'en-US'

export const THEME_KEY = 'oms-theme'
export const LOCALE_KEY = 'oms-locale'

export function applyTheme(theme: ThemeMode) {
  if (theme === 'dark') {
    document.body.setAttribute('arco-theme', 'dark')
  } else {
    document.body.removeAttribute('arco-theme')
  }
}

function readTheme(): ThemeMode {
  const value = localStorage.getItem(THEME_KEY)
  return value === 'dark' ? 'dark' : 'light'
}

function readLocale(): LocaleCode {
  const value = localStorage.getItem(LOCALE_KEY)
  return value === 'en-US' ? 'en-US' : 'zh-CN'
}

export const useAppStore = defineStore('app', {
  state: () => ({
    locale: readLocale() as LocaleCode,
    theme: readTheme() as ThemeMode,
  }),
  actions: {
    setLocale(locale: LocaleCode) {
      this.locale = locale
      localStorage.setItem(LOCALE_KEY, locale)
      setI18nLocale(locale)
    },
    setTheme(theme: ThemeMode) {
      this.theme = theme
      localStorage.setItem(THEME_KEY, theme)
      applyTheme(theme)
    },
    toggleTheme() {
      this.setTheme(this.theme === 'dark' ? 'light' : 'dark')
    },
    init() {
      applyTheme(this.theme)
    },
  },
})

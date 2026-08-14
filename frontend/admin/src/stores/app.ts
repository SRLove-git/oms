import { defineStore } from 'pinia'

import { getInitialLocale, LOCALE_KEY, type Locale } from '@/i18n'

export type Theme = 'light' | 'dark'

const THEME_KEY = 'oms-theme'

function readTheme(): Theme {
  return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light'
}

export const useAppStore = defineStore('app', {
  state: () => ({
    locale: getInitialLocale(),
    theme: readTheme(),
    collapsed: false,
  }),
  actions: {
    toggleCollapsed() {
      this.collapsed = !this.collapsed
    },
    setLocale(locale: Locale) {
      this.locale = locale
      localStorage.setItem(LOCALE_KEY, locale)
    },
    setTheme(theme: Theme) {
      this.theme = theme
      localStorage.setItem(THEME_KEY, theme)
      this.applyTheme()
    },
    toggleTheme() {
      this.setTheme(this.theme === 'dark' ? 'light' : 'dark')
    },
    applyTheme() {
      if (this.theme === 'dark') {
        document.body.setAttribute('arco-theme', 'dark')
      } else {
        document.body.removeAttribute('arco-theme')
      }
    },
  },
})

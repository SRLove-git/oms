import { useSyncExternalStore } from 'react'

import i18n from '@/i18n'

export type Locale = 'zh-CN' | 'en-US'
export type Theme = 'light' | 'dark'

const LOCALE_KEY = 'oms-locale'
const THEME_KEY = 'oms-theme'

export interface AppState {
  locale: Locale
  theme: Theme
}

function readLocale(): Locale {
  return localStorage.getItem(LOCALE_KEY) === 'en-US' ? 'en-US' : 'zh-CN'
}

function readTheme(): Theme {
  return localStorage.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light'
}

function createAppStore() {
  let state: AppState = {
    locale: readLocale(),
    theme: readTheme(),
  }
  const listeners = new Set<() => void>()

  function emit() {
    listeners.forEach((listener) => listener())
  }

  return {
    init() {
      state = { locale: readLocale(), theme: readTheme() }
      emit()
    },

    setLocale(locale: Locale) {
      state = { ...state, locale }
      localStorage.setItem(LOCALE_KEY, locale)
      i18n.changeLanguage(locale)
      emit()
    },

    setTheme(theme: Theme) {
      state = { ...state, theme }
      localStorage.setItem(THEME_KEY, theme)
      emit()
    },

    toggleTheme() {
      this.setTheme(state.theme === 'dark' ? 'light' : 'dark')
    },

    subscribe(listener: () => void) {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },

    getSnapshot(): AppState {
      return state
    },
  }
}

export const appStore = createAppStore()

export function useAppStore(): AppState {
  return useSyncExternalStore(appStore.subscribe, appStore.getSnapshot)
}

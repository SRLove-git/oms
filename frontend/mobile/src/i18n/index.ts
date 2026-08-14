import { createI18n } from 'vue-i18n'

import type { LocaleCode } from '@/stores/app'
import enUS from './locales/en-US'
import zhCN from './locales/zh-CN'

export const LOCALE_KEY = 'oms-locale'

function readLocale(): LocaleCode {
  const value = localStorage.getItem(LOCALE_KEY)
  return value === 'en-US' ? 'en-US' : 'zh-CN'
}

const i18n = createI18n({
  legacy: false,
  locale: readLocale(),
  fallbackLocale: 'zh-CN',
  messages: {
    'zh-CN': zhCN,
    'en-US': enUS,
  },
})

export function setI18nLocale(locale: LocaleCode) {
  i18n.global.locale.value = locale
}

export default i18n

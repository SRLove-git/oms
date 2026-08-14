import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import enUS from './locales/en-US'
import zhCN from './locales/zh-CN'

const lng = localStorage.getItem('oms-locale') === 'en-US' ? 'en-US' : 'zh-CN'

i18n.use(initReactI18next).init({
  resources: {
    'zh-CN': { translation: zhCN },
    'en-US': { translation: enUS },
  },
  lng,
  fallbackLng: 'zh-CN',
  interpolation: {
    escapeValue: false,
  },
  initAsync: false,
})

export default i18n

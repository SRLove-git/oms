import { useEffect } from 'react'
import { ConfigProvider } from '@arco-design/web-react'
import enUS from '@arco-design/web-react/es/locale/en-US'
import zhCN from '@arco-design/web-react/es/locale/zh-CN'
import { RouterProvider } from 'react-router-dom'

import { router } from './router'
import { appStore, useAppStore } from './stores/app'
import { userStore } from './stores/user'

userStore.init()
appStore.init()

const ARCO_LOCALES = {
  'zh-CN': zhCN,
  'en-US': enUS,
} as const

export default function App() {
  const { locale, theme } = useAppStore()

  useEffect(() => {
    if (theme === 'dark') {
      document.body.setAttribute('arco-theme', 'dark')
    } else {
      document.body.removeAttribute('arco-theme')
    }
  }, [theme])

  return (
    <ConfigProvider locale={ARCO_LOCALES[locale]}>
      <RouterProvider router={router} />
    </ConfigProvider>
  )
}

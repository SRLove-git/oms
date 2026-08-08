import { ConfigProvider } from '@arco-design/web-react'
import zhCN from '@arco-design/web-react/es/locale/zh-CN'
import { RouterProvider } from 'react-router-dom'

import { router } from './router'
import { userStore } from './stores/user'

userStore.init()

export default function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <RouterProvider router={router} />
    </ConfigProvider>
  )
}

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import ArcoVue from '@arco-design/web-vue'
import '@arco-design/web-vue/dist/arco.css'

import App from './App.vue'
import i18n from './i18n'
import router from './router'
import { useAppStore } from './stores/app'
import './styles/index.css'

const app = createApp(App)

const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ArcoVue)
app.use(i18n)

// 应用启动时按持久化的主题初始化暗色/亮色模式
useAppStore(pinia).applyTheme()

app.mount('#app')

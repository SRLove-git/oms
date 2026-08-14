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
app.use(i18n)
app.use(ArcoVue)

useAppStore(pinia).init()

app.mount('#app')

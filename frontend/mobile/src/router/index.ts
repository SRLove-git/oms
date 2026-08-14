import { createRouter, createWebHistory } from 'vue-router'

import TabLayout from '@/layouts/TabLayout.vue'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: 'login.title' },
    },
    {
      path: '/',
      component: TabLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: 'tabbar.home', tab: 'home' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrderListView.vue'),
          meta: { title: 'tabbar.orders', tab: 'orders' },
        },
        {
          path: 'aftersales',
          name: 'aftersales',
          component: () => import('@/views/AfterSalesView.vue'),
          meta: { title: 'tabbar.aftersales', tab: 'aftersales' },
        },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
          meta: { title: 'tabbar.profile', tab: 'profile' },
        },
      ],
    },
    {
      path: '/sku/:id',
      name: 'sku-detail',
      component: () => import('@/views/SkuDetailView.vue'),
      meta: { title: 'skuDetail.title' },
    },
    {
      path: '/checkout',
      name: 'checkout',
      component: () => import('@/views/CheckoutView.vue'),
      meta: { title: 'checkout.title' },
    },
    {
      path: '/orders/:orderNo',
      name: 'order-detail',
      component: () => import('@/views/OrderDetailView.vue'),
      meta: { title: 'orders.detail' },
    },
    {
      path: '/aftersales/apply',
      name: 'aftersales-apply',
      component: () => import('@/views/AfterSalesApplyView.vue'),
      meta: { title: 'aftersales.applyTitle' },
    },
  ],
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!userStore.isLoggedIn && to.name !== 'login') {
    return { name: 'login' }
  }
  if (userStore.isLoggedIn && to.name === 'login') {
    return { name: 'home' }
  }
  return true
})

export default router

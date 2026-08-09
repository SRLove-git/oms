import { createRouter, createWebHistory } from 'vue-router'

import BasicLayout from '@/layouts/BasicLayout.vue'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/',
      component: BasicLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: '首页' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrdersView.vue'),
          meta: { title: '订单管理' },
        },
        {
          path: 'products',
          name: 'products',
          component: () => import('@/views/ProductsView.vue'),
          meta: { title: '商品管理' },
        },
        {
          path: 'inventories',
          name: 'inventories',
          component: () => import('@/views/InventoriesView.vue'),
          meta: { title: '库存管理' },
        },
        {
          path: 'payments',
          name: 'payments',
          component: () => import('@/views/PaymentsView.vue'),
          meta: { title: '支付记录' },
        },
        {
          path: 'merchants',
          name: 'merchants',
          component: () => import('@/views/MerchantsView.vue'),
          meta: { title: '商户管理' },
        },
        {
          path: 'qualifications',
          name: 'qualifications',
          component: () => import('@/views/QualificationsView.vue'),
          meta: { title: '资质管理' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '用户管理' },
        },
        {
          path: 'audit-logs',
          name: 'audit-logs',
          component: () => import('@/views/AuditLogsView.vue'),
          meta: { title: '审计日志' },
        },
        {
          path: 'after-sales',
          name: 'after-sales',
          component: () => import('@/views/AfterSalesView.vue'),
          meta: { title: '售后服务' },
        },
        {
          path: 'reconciliation',
          name: 'reconciliation',
          component: () => import('@/views/ReconciliationView.vue'),
          meta: { title: '支付对账' },
        },
        {
          path: 'logistics',
          name: 'logistics',
          component: () => import('@/views/LogisticsView.vue'),
          meta: { title: '物流轨迹' },
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/NotificationsView.vue'),
          meta: { title: '消息通知' },
        },
        {
          path: 'reports/sales',
          name: 'reports-sales',
          component: () => import('@/views/SalesReportView.vue'),
          meta: { title: '销售报表' },
        },
        {
          path: 'reports/inventory',
          name: 'reports-inventory',
          component: () => import('@/views/InventoryReportView.vue'),
          meta: { title: '库存报表' },
        },
        {
          path: 'reports/payments',
          name: 'reports-payments',
          component: () => import('@/views/PaymentReportView.vue'),
          meta: { title: '支付报表' },
        },
        {
          path: 'reports/aftersales',
          name: 'reports-aftersales',
          component: () => import('@/views/AfterSalesReportView.vue'),
          meta: { title: '售后报表' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  if (!userStore.isLoggedIn && to.name !== 'login') {
    return { name: 'login' }
  }
  return true
})

export default router

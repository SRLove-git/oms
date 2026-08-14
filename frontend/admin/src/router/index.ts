import { createRouter, createWebHistory } from 'vue-router'

import BasicLayout from '@/layouts/BasicLayout.vue'
import { useUserStore } from '@/stores/user'

import { updateDocumentTitle } from './title'

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
      component: BasicLayout,
      redirect: '/home',
      children: [
        {
          path: 'home',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
          meta: { title: 'menu.home' },
        },
        {
          path: 'orders',
          name: 'orders',
          component: () => import('@/views/OrdersView.vue'),
          meta: { title: 'menu.orders' },
        },
        {
          path: 'products',
          name: 'products',
          component: () => import('@/views/ProductsView.vue'),
          meta: { title: 'menu.products' },
        },
        {
          path: 'inventories',
          name: 'inventories',
          component: () => import('@/views/InventoriesView.vue'),
          meta: { title: 'menu.inventories' },
        },
        {
          path: 'payments',
          name: 'payments',
          component: () => import('@/views/PaymentsView.vue'),
          meta: { title: 'menu.payments' },
        },
        {
          path: 'merchants',
          name: 'merchants',
          component: () => import('@/views/MerchantsView.vue'),
          meta: { title: 'menu.merchants' },
        },
        {
          path: 'qualifications',
          name: 'qualifications',
          component: () => import('@/views/QualificationsView.vue'),
          meta: { title: 'menu.qualifications' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: 'menu.users' },
        },
        {
          path: 'audit-logs',
          name: 'audit-logs',
          component: () => import('@/views/AuditLogsView.vue'),
          meta: { title: 'menu.auditLogs' },
        },
        {
          path: 'after-sales',
          name: 'after-sales',
          component: () => import('@/views/AfterSalesView.vue'),
          meta: { title: 'menu.afterSales' },
        },
        {
          path: 'reconciliation',
          name: 'reconciliation',
          component: () => import('@/views/ReconciliationView.vue'),
          meta: { title: 'menu.reconciliation' },
        },
        {
          path: 'logistics',
          name: 'logistics',
          component: () => import('@/views/LogisticsView.vue'),
          meta: { title: 'menu.logistics' },
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/NotificationsView.vue'),
          meta: { title: 'menu.notifications' },
        },
        {
          path: 'reports/sales',
          name: 'reports-sales',
          component: () => import('@/views/SalesReportView.vue'),
          meta: { title: 'menu.reportsSales' },
        },
        {
          path: 'reports/inventory',
          name: 'reports-inventory',
          component: () => import('@/views/InventoryReportView.vue'),
          meta: { title: 'menu.reportsInventory' },
        },
        {
          path: 'reports/payments',
          name: 'reports-payments',
          component: () => import('@/views/PaymentReportView.vue'),
          meta: { title: 'menu.reportsPayments' },
        },
        {
          path: 'reports/aftersales',
          name: 'reports-aftersales',
          component: () => import('@/views/AfterSalesReportView.vue'),
          meta: { title: 'menu.reportsAftersales' },
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
  updateDocumentTitle(to)
  return true
})

export default router

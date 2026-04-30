import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'

const routes: RouteRecordRaw[] = [
  {
    path: '/mobile/order',
    name: 'MobileParkOrder',
    component: () => import('@/views/mobile/ParkOrder.vue'),
    meta: { title: '移动下单' },
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '调度看板', breadcrumb: ['调度看板'] },
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('@/views/order/List.vue'),
        meta: { title: '订单管理', breadcrumb: ['订单管理'] },
      },
      {
        path: 'orders/:orderId',
        name: 'OrderDetail',
        component: () => import('@/views/order/Detail.vue'),
        meta: { title: '订单详情', breadcrumb: ['订单管理', '订单详情'] },
      },
      {
        path: 'tasks',
        name: 'TaskList',
        component: () => import('@/views/task/List.vue'),
        meta: { title: '调度任务', breadcrumb: ['调度任务'] },
      },
      {
        path: 'tasks/:taskId',
        name: 'TaskDetail',
        component: () => import('@/views/task/Detail.vue'),
        meta: { title: '任务详情', breadcrumb: ['调度任务', '任务详情'] },
      },
      {
        path: 'vehicles',
        name: 'VehicleList',
        component: () => import('@/views/vehicle/List.vue'),
        meta: { title: '车辆管理', breadcrumb: ['车辆管理'] },
      },
      {
        path: 'vehicle-tracking',
        name: 'VehicleTracking',
        component: () => import('@/views/vehicle/Tracking.vue'),
        meta: { title: '车辆监控大屏', breadcrumb: ['车辆监控大屏'], fullscreen: true },
      },
      {
        path: 'vehicles/:vehicleId',
        name: 'VehicleDetail',
        component: () => import('@/views/vehicle/Detail.vue'),
        meta: { title: '车辆详情', breadcrumb: ['车辆管理', '车辆详情'] },
      },
      {
        path: 'exceptions',
        name: 'ExceptionList',
        component: () => import('@/views/exception/Index.vue'),
        meta: { title: '异常任务', breadcrumb: ['异常任务'] },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

export default router

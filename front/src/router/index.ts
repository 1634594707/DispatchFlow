import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { ADMIN_AUTH_ENABLED } from '@/config'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/mobile/order',
    name: 'MobileParkOrder',
    component: () => import('@/views/mobile/ParkOrder.vue'),
    meta: { title: '移动下单', public: true },
  },
  {
    path: '/',
    component: BasicLayout,
    redirect: '/workbench',
    children: [
      {
        path: 'workbench',
        name: 'Workbench',
        component: () => import('@/views/workbench/Index.vue'),
        meta: { title: '调度工作台', breadcrumb: ['调度工作台'] },
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Index.vue'),
        meta: { title: '调度看板', breadcrumb: ['调度看板'] },
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('@/views/analytics/Index.vue'),
        meta: { title: '运营分析', breadcrumb: ['运营分析'] },
      },
      {
        path: 'analytics/charging',
        name: 'AnalyticsCharging',
        component: () => import('@/views/analytics/ChargingReport.vue'),
        meta: { title: '充电报表', breadcrumb: ['运营分析', '充电报表'] },
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
      {
        path: 'infrastructure/parks',
        name: 'InfraParkList',
        component: () => import('@/views/infrastructure/ParkList.vue'),
        meta: { title: '园区管理', breadcrumb: ['基础设施', '园区管理'], requiresAdmin: true },
      },
      {
        path: 'infrastructure/stations',
        name: 'InfraStationList',
        component: () => import('@/views/infrastructure/StationList.vue'),
        meta: { title: '站点管理', breadcrumb: ['基础设施', '站点管理'], requiresAdmin: true },
      },
      {
        path: 'infrastructure/parking-slots',
        name: 'InfraParkingSlotList',
        component: () => import('@/views/infrastructure/ParkingSlotList.vue'),
        meta: { title: '停车位管理', breadcrumb: ['基础设施', '停车位管理'], requiresAdmin: true },
      },
      {
        path: 'infrastructure/charging-piles',
        name: 'InfraChargingPileList',
        component: () => import('@/views/infrastructure/ChargingPileList.vue'),
        meta: { title: '充电桩管理', breadcrumb: ['基础设施', '充电桩管理'], requiresAdmin: true },
      },
      {
        path: 'infrastructure/road-network',
        name: 'InfraRoadNetwork',
        component: () => import('@/views/infrastructure/RoadNetwork.vue'),
        meta: { title: '路网管理', breadcrumb: ['基础设施', '路网管理'], requiresAdmin: true },
      },
      {
        path: 'system/users',
        name: 'UserList',
        component: () => import('@/views/system/UserList.vue'),
        meta: { title: '用户管理', breadcrumb: ['系统管理', '用户管理'], requiresAdmin: true },
      },
      {
        path: 'system/operate-logs',
        name: 'OperateLogList',
        component: () => import('@/views/system/OperateLogList.vue'),
        meta: { title: '操作日志', breadcrumb: ['系统管理', '操作日志'], requiresAdmin: true },
      },
      {
        path: 'system/alert-settings',
        name: 'AlertSettings',
        component: () => import('@/views/system/AlertSettings.vue'),
        meta: { title: '告警设置', breadcrumb: ['系统管理', '告警设置'] },
      },
      {
        path: 'system/dispatch-strategy',
        name: 'DispatchStrategy',
        component: () => import('@/views/system/DispatchStrategy.vue'),
        meta: { title: '调度策略', breadcrumb: ['系统管理', '调度策略'], requiresAdmin: true },
      },
      {
        path: 'system/integration',
        name: 'Integration',
        component: () => import('@/views/system/Integration.vue'),
        meta: { title: '外部集成', breadcrumb: ['系统管理', '外部集成'], requiresAdmin: true },
      },
      {
        path: 'infrastructure/traffic',
        name: 'InfraTraffic',
        component: () => import('@/views/infrastructure/TrafficOverview.vue'),
        meta: { title: '交通态势', breadcrumb: ['基础设施', '交通态势'], requiresAdmin: true },
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/system/Profile.vue'),
        meta: { title: '个人设置', breadcrumb: ['个人设置'] },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()

  if (to.meta.public) {
    if (to.path === '/login' && authStore.token) {
      const ok = await authStore.ensureAuth()
      if (ok) {
        return { path: '/workbench' }
      }
    }
    return true
  }

  if (ADMIN_AUTH_ENABLED) {
    if (!authStore.token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
    const ok = await authStore.ensureAuth()
    if (!ok) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return { path: '/workbench' }
  }

  return true
})

export default router

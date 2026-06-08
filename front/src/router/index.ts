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
        meta: { title: '调度看板', breadcrumb: ['调度运营', '调度看板'] },
      },
      {
        path: 'analytics',
        name: 'Analytics',
        component: () => import('@/views/analytics/Index.vue'),
        meta: { title: '运营分析', breadcrumb: ['分析监控', '运营分析'] },
      },
      {
        path: 'analytics/charging',
        name: 'AnalyticsCharging',
        component: () => import('@/views/analytics/ChargingReport.vue'),
        meta: { title: '充电报表', breadcrumb: ['分析监控', '充电报表'] },
      },
      {
        path: 'analytics/custom-report',
        name: 'AnalyticsCustomReport',
        component: () => import('@/views/analytics/CustomReport.vue'),
        meta: { title: '自定义报表', breadcrumb: ['分析监控', '自定义报表'] },
      },
      {
        path: 'analytics/report-history',
        name: 'AnalyticsReportHistory',
        component: () => import('@/views/analytics/ReportHistory.vue'),
        meta: { title: '历史报表', breadcrumb: ['分析监控', '历史报表'] },
      },
      {
        path: 'orders',
        name: 'OrderList',
        component: () => import('@/views/order/List.vue'),
        meta: { title: '订单管理', breadcrumb: ['调度运营', '订单管理'] },
      },
      {
        path: 'orders/:orderId',
        name: 'OrderDetail',
        component: () => import('@/views/order/Detail.vue'),
        meta: { title: '订单详情', breadcrumb: ['调度运营', '订单管理', '订单详情'] },
      },
      {
        path: 'tasks',
        name: 'TaskList',
        component: () => import('@/views/task/List.vue'),
        meta: { title: '调度任务', breadcrumb: ['调度运营', '调度任务'] },
      },
      {
        path: 'tasks/:taskId',
        name: 'TaskDetail',
        component: () => import('@/views/task/Detail.vue'),
        meta: { title: '任务详情', breadcrumb: ['调度运营', '调度任务', '任务详情'] },
      },
      {
        path: 'vehicles',
        name: 'VehicleList',
        component: () => import('@/views/vehicle/List.vue'),
        meta: { title: '车辆管理', breadcrumb: ['车队资源', '车辆管理'] },
      },
      {
        path: 'vehicle-tracking',
        name: 'VehicleTracking',
        component: () => import('@/views/vehicle/Tracking.vue'),
        meta: { title: '车辆监控大屏', breadcrumb: ['分析监控', '车辆监控大屏'], fullscreen: true },
      },
      {
        path: 'gis/park-overview',
        name: 'ParkOverview',
        component: () => import('@/views/gis/ParkOverview.vue'),
        meta: { title: '多园区总览', breadcrumb: ['分析监控', '多园区总览'], fullscreen: true },
      },
      {
        path: 'dev/map-poc',
        name: 'MapPoc',
        component: () => import('@/views/dev/MapPoc.vue'),
        meta: { title: '高德地图 PoC', breadcrumb: ['开发验证', '高德地图 PoC'], requiresAdmin: true },
      },
      {
        path: 'vehicles/:vehicleId',
        name: 'VehicleDetail',
        component: () => import('@/views/vehicle/Detail.vue'),
        meta: { title: '车辆详情', breadcrumb: ['车队资源', '车辆管理', '车辆详情'] },
      },
      {
        path: 'exceptions',
        name: 'ExceptionList',
        component: () => import('@/views/exception/Index.vue'),
        meta: { title: '异常任务', breadcrumb: ['调度运营', '异常任务'] },
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
        path: 'infrastructure/swap-cabinets',
        name: 'InfraSwapCabinetList',
        component: () => import('@/views/infrastructure/SwapCabinetList.vue'),
        meta: { title: '换电柜管理', breadcrumb: ['基础设施', '换电柜管理'], requiresAdmin: true },
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
        path: 'system/report-schedule',
        name: 'ReportSchedule',
        component: () => import('@/views/system/ReportSchedule.vue'),
        meta: { title: '定时报表', breadcrumb: ['系统管理', '定时报表'], requiresAdmin: true },
      },
      {
        path: 'system/security',
        name: 'SecuritySettings',
        component: () => import('@/views/system/SecuritySettings.vue'),
        meta: { title: '安全设置', breadcrumb: ['系统管理', '安全设置'], requiresAdmin: true },
      },
      {
        path: 'field-ops/tickets',
        name: 'FieldOpsTickets',
        component: () => import('@/views/field-ops/Tickets.vue'),
        meta: { title: '现场工单', breadcrumb: ['产业运营', '现场工单'], fieldOps: true },
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
        path: 'infrastructure/geofences',
        name: 'InfraGeofenceList',
        component: () => import('@/views/infrastructure/GeofenceList.vue'),
        meta: { title: '地理围栏', breadcrumb: ['基础设施', '地理围栏'], requiresAdmin: true },
      },
      {
        path: 'digital-twin',
        name: 'DigitalTwin',
        component: () => import('@/views/digital-twin/Index.vue'),
        meta: { title: '数字孪生', breadcrumb: ['数字孪生'] },
      },
      {
        path: 'vertical/routes',
        name: 'VerticalRoutes',
        component: () => import('@/views/vertical/RouteList.vue'),
        meta: { title: '线路管理', breadcrumb: ['产业运营', '线路管理'], requiresAdmin: true },
      },
      {
        path: 'vertical/hub',
        name: 'VerticalHub',
        component: () => import('@/views/vertical/HubOverview.vue'),
        meta: { title: '母港分流', breadcrumb: ['产业运营', '母港分流'] },
      },
      {
        path: 'vertical/peak-mode',
        name: 'VerticalPeakMode',
        component: () => import('@/views/vertical/PeakMode.vue'),
        meta: { title: '高峰预案', breadcrumb: ['产业运营', '高峰预案'], requiresAdmin: true },
      },
      {
        path: 'vertical/automation-rules',
        name: 'VerticalAutomationRules',
        component: () => import('@/views/vertical/AutomationRules.vue'),
        meta: { title: '自动化规则', breadcrumb: ['产业运营', '自动化规则'], requiresAdmin: true },
      },
      {
        path: 'vertical/ops-snapshot',
        name: 'VerticalOpsSnapshot',
        component: () => import('@/views/vertical/OpsSnapshot.vue'),
        meta: { title: '运维快照', breadcrumb: ['产业运营', '运维快照'] },
      },
      {
        path: 'system/config-check',
        name: 'ConfigCheck',
        component: () => import('@/views/system/ConfigCheck.vue'),
        meta: { title: '试点配置自检', breadcrumb: ['系统管理', '试点配置自检'], requiresAdmin: true },
      },
      {
        path: 'system/health',
        name: 'SystemHealth',
        component: () => import('@/views/system/SystemHealth.vue'),
        meta: { title: '系统健康', breadcrumb: ['系统管理', '系统健康'], requiresAdmin: true },
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

  if (authStore.isFieldOps) {
    const allowed = to.meta.fieldOps || to.path === '/profile' || to.meta.public
    if (!allowed) {
      return { path: '/field-ops/tickets' }
    }
  }

  return true
})

export default router

export const API_BASE = '/api'

export const DEFAULT_PAGE_SIZE = 20
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

export const DASHBOARD_POLL_INTERVAL = 30000

export const REQUEST_TIMEOUT = 10000

/** 默认开启；本地免登录开发可设 VITE_ADMIN_AUTH_ENABLED=false */
export const ADMIN_AUTH_ENABLED = import.meta.env.VITE_ADMIN_AUTH_ENABLED !== 'false'

/** 默认车辆追踪场景：'park' 园区调度 | 'delivery' 短驳地理 */
export const DEFAULT_TRACKING_SCENE = 'park'

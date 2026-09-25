export const API_BASE = '/api'

export const DEFAULT_PAGE_SIZE = 20
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100]

export const DASHBOARD_POLL_INTERVAL = 30000

/** §6.4：实时 store 的两个节奏常数集中于此（原散落在 `stores/realtime.ts`）。 */
export const REALTIME_REFRESH_COALESCE_MS = 250
export const REALTIME_FALLBACK_POLL_MS = 30_000

/**
 * §6.5：GIS 总览的**兜底**轮询节奏。
 *
 * <p>原来这里是写死的 3 秒且无条件打 5 个端点（园区总览 + 车/单/围栏/站点）⇒ 一个开着的
 * 标签页就是 100 请求/分钟，切到后台也不停。SSE 在连接时刷新由 `realtime.subscribeRefresh`
 * 事件驱动，这个定时器只承担"流没连上时也别永久停在旧数据"的兜底职责。</p>
 */
export const PARK_OVERVIEW_POLL_MS = 30_000

/**
 * GIS 总览两次取数之间的最小间隔。
 *
 * <p>实测首屏会出现"onMounted 取完 → store 的降级广播（250 ms 合并）再触发一趟"，
 * 于是 5 个端点被打两遍。事件突发时同理（一次派单变更会连着广播多个 refresh）。
 * 这个窗口把"刚取完就重复取"吃掉，代价是最坏情况下一次真实变更晚 2 s 才反映到图上。</p>
 */
export const PARK_OVERVIEW_MIN_REFRESH_GAP_MS = 2_000

export const REQUEST_TIMEOUT = 10000

/** 默认开启；本地免登录开发可设 VITE_ADMIN_AUTH_ENABLED=false */
export const ADMIN_AUTH_ENABLED = import.meta.env.VITE_ADMIN_AUTH_ENABLED !== 'false'

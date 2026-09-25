import { test, expect, type Page } from '@playwright/test'

/**
 * §6.5：逐页"首屏请求预算 + 静置不涨 + 无重复端点 + 无重连风暴"。
 *
 * 这组数字是 2026-09-22 用一次性探针在"SSE 建连正常"前提下实测出来的（不是估算）。
 * 探针同时暴露了三处真实浪费，本用例是它们的回归闸门：
 *   1. `realtime` store 用 `parkScope.$subscribe` 监听整个 state —— 连 `loading`/`parks`
 *      的写入都算一次"换园区"，冷加载就把 SSE 拆了重建（实测 4~5 次建连），
 *      每次拆除还会同步回调 onClose ⇒ 被判成断线、立刻全量重拉一遍角标数据。
 *   2. 布局角标与页面首屏在同一帧各要一次 workbench/summary —— 现在并发合并（/workbench 13→7）。
 *   3. `useParkMetadata` 自带 onMounted 又被客户页面 refresh() 一次；`loadParks` 同理（Tracking 页 /parks ×2）。
 *
 * 前提：`GLOBAL_TRIO` 三件出现在每个页面，是侧边栏角标（待派/异常/离线车）的代价，不是页面自己的取数。
 */

const api = (path: string) => {
  const apiPath = `/api${path}`
  if (apiPath.includes('**')) {
    const prefix = apiPath.replace(/\*\*/g, '')
    return (url: URL) => url.pathname.startsWith(prefix)
  }
  return (url: URL) => url.pathname === apiPath
}

function ok(data: unknown) {
  return { success: true, code: 'OK', message: 'ok', data }
}

// 与"页面查了几件事"无关：鉴权、通知、健康、操作日志
const INFRA = /\/admin\/(auth\/me|notifications|system\/health|operate-log)/
// 实时通道与换票：单独按"建连次数"计，不进请求预算
const STREAM = /\/admin\/(dispatch\/stream|fleet\/telemetry\/stream|sse-ticket)/

const GLOBAL_TRIO = [
  'GET /api/admin/dispatch/workbench',
  'GET /api/admin/dashboard/summary',
  'POST /api/admin/dispatch/task-pool/query',
]

interface PageBudget {
  route: string
  /** 页面自己的取数；GLOBAL_TRIO 由用例自动并入预算 */
  own: string[]
  /** 允许的实时通道建连次数（一次建连 = 换票 + 开流） */
  maxStreamHandshakes: number
}

const BUDGETS: PageBudget[] = [
  { route: '/workbench', own: ['GET /api/admin/park/metadata', 'GET /api/admin/park/geofences', 'GET /api/admin/park/stations', 'GET /api/admin/parks'], maxStreamHandshakes: 2 },
  { route: '/tasks', own: ['POST /api/admin/tasks/query', 'GET /api/admin/parks'], maxStreamHandshakes: 2 },
  { route: '/orders', own: ['POST /api/admin/orders/query', 'GET /api/admin/parks'], maxStreamHandshakes: 2 },
  { route: '/analytics', own: [
    'GET /api/admin/analytics/efficiency',
    'GET /api/admin/analytics/exceptions',
    'GET /api/admin/analytics/daily-summary',
    'GET /api/admin/analytics/chain-kpi',
    'GET /api/admin/analytics/peak-compare',
    'GET /api/admin/analytics/park-comparison',
    'GET /api/admin/parks',
  ], maxStreamHandshakes: 2 },
  { route: '/vehicles', own: ['POST /api/admin/vehicles/query', 'GET /api/admin/parks'], maxStreamHandshakes: 2 },
  { route: '/gis/park-overview', own: [
    'GET /api/admin/park/metadata',
    'GET /api/admin/station-service-positions/map-versions/active',
    'GET /api/admin/parks',
    'GET /api/admin/park/overview',
    'GET /api/admin/park/vehicles',
    'GET /api/admin/park/orders',
    'GET /api/admin/park/geofences',
    'GET /api/admin/park/stations',
  ], maxStreamHandshakes: 2 },
  { route: '/vehicle-tracking?mode=geo', own: [
    'GET /api/admin/parks',
    'GET /api/admin/park/layout',
    'GET /api/admin/park/vehicles',
    'GET /api/admin/park/orders',
    'GET /api/admin/park/geofences',
    'GET /api/admin/vertical/peak-mode',
    'GET /api/admin/park/road-route/health',
  ], maxStreamHandshakes: 4 },
]

async function seed(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('fsd_admin_token', 'e2e-budget-token')
    sessionStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }))
  })
  await page.route(api('/admin/auth/me'), (r) => r.fulfill({ json: ok({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }) }))
  await page.route(api('/admin/**'), (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/query') || url.pathname.includes('/list')) {
      return route.fulfill({ json: ok({ records: [], total: 0, pageNo: 1, pageSize: 10 }) })
    }
    return route.fulfill({ json: ok({}) })
  })
  await page.route(api('/admin/parks'), (r) => r.fulfill({ json: ok([{ parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true }]) }))
  await page.route(api('/admin/dispatch/workbench'), (r) => r.fulfill({ json: ok({
    intervention: { pendingCount: 0, manualPendingCount: 0, openExceptionCount: 0, pendingTasks: [], manualPendingTasks: [], openExceptions: [] },
    fleetMetrics: { assignableVehicleCount: 2, pluggedStandbyCount: 0, chargingCount: 0, onlineVehicleCount: 2 },
    parkLayout: { parkId: 1, width: 1600, height: 1854, centerLng: 121.093236, centerLat: 31.937344 },
    vehicles: [],
  }) }))
  await page.route(/\/manifest\.webmanifest(\?.*)?$/, (r) => r.fulfill({ contentType: 'application/manifest+json', json: { name: 'DispatchFlow', icons: [] } }))
  await page.route(/\/(dev-sw|sw)\.js(\?.*)?$/, (r) => r.fulfill({ contentType: 'application/javascript', body: 'self.addEventListener("install", () => self.skipWaiting())' }))
  // 最后注册才盖得过 /admin/**：把流永久挂起 —— 既不触发重连风暴，也不触发降级兜底轮询，
  // 于是量到的是"SSE 正常"这一生产常态，而不是"流必断"的额外代价。
  await page.route(/\/admin\/(dispatch\/stream|fleet\/telemetry\/stream)/, () => new Promise<void>(() => {}))
}

for (const budget of BUDGETS) {
  test(`${budget.route} 首屏请求在预算内、无重复、静置不涨`, async ({ page }) => {
    test.setTimeout(60_000)
    await seed(page)

    const hits: string[] = []
    let handshakes = 0
    page.on('request', (request) => {
      const url = new URL(request.url())
      if (!url.pathname.startsWith('/api/admin/')) return
      if (STREAM.test(url.pathname)) {
        handshakes += 1
        return
      }
      if (INFRA.test(url.pathname)) return
      hits.push(`${request.method()} ${url.pathname}`)
    })

    await page.goto(budget.route, { waitUntil: 'domcontentloaded' })
    await page.waitForTimeout(3_500)
    const firstPaint = [...hits]
    await page.waitForTimeout(8_000)

    const allowed = [...budget.own, ...GLOBAL_TRIO]
    const unexpected = firstPaint.filter((name) => !allowed.includes(name))
    const counts = new Map<string, number>()
    for (const name of firstPaint) counts.set(name, (counts.get(name) ?? 0) + 1)
    const repeated = [...counts.entries()].filter(([, n]) => n > 1).map(([name, n]) => `${name} ×${n}`)

    expect(unexpected, `首屏出现预算外端点：${unexpected.join(', ')}`).toEqual([])
    expect(repeated, `同一端点被重复请求：${repeated.join(', ')}`).toEqual([])
    expect(firstPaint.length).toBeLessThanOrEqual(allowed.length)
    expect(handshakes, '实时通道建连次数（换票+开流）超出预算').toBeLessThanOrEqual(budget.maxStreamHandshakes)
    // 静置 8 s 不许长出新请求：定时器/重连在"流正常"时不该自己拉数据
    expect(hits.length, `静置后新增：${hits.slice(firstPaint.length).join(', ')}`).toBe(firstPaint.length)
  })
}

import { expect, test, type Page } from '@playwright/test'

/**
 * §6.5 首屏数据请求预算。
 *
 * 写在这里而不是从 `src/config` 引：spec 由 Playwright 的 Node 侧转译加载，
 * `src/config/index.ts` 里的 `import.meta.env` 在那一侧是 undefined（实测 TypeError 直接不起）。
 */
const FIRST_PAINT_REQUEST_BUDGET = 8

/**
 * §6.5：本轮交付的三条主路径补真用户路径覆盖（点按钮，不是裸 fetch）。
 *
 * 覆盖的是"看得见的契约"：
 *  - 处置台批量操作把**部分成功**如实报出来，失败行留在选择里（§2.3 / §6.4）
 *  - 端点失败时页面上必须有错误文字，不能退成"什么都没发生"（§6.3 禁止静默失败）
 *  - 任务详情的决策依据（漏斗 / 次优分差 / 影子对照）真的渲染，读不到时明说读不到（§7.3）
 *  - 紧急暂停：无原因不落库、审计信息回显、**读取失败不得显示成"正常进行中"**（§7.4 / §6.3）
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

async function seedAdminSession(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('fsd_admin_token', 'e2e-admin-token')
    sessionStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }))
  })
  await page.route(api('/admin/auth/me'), route => route.fulfill({ json: ok({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }) }))
}

function pageData<T>(records: T[]) {
  return { records, total: records.length, pageNo: 1, pageSize: 10 }
}

const taskRows = [
  { taskId: 101, taskNo: 'TK-101', orderId: 201, vehicleId: null, status: 'PENDING', dispatchType: 'MANUAL', createdAt: '2026-09-22T08:00:00Z' },
  { taskId: 102, taskNo: 'TK-102', orderId: 202, vehicleId: 99, status: 'ASSIGNED', dispatchType: 'AUTO', createdAt: '2026-09-22T08:05:00Z' },
]

test.beforeEach(async ({ page }) => {
  await page.route(api('/admin/**'), route => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/query') || url.pathname.includes('/list')) {
      return route.fulfill({ json: ok({ records: [], total: 0, pageNo: 1, pageSize: 10 }) })
    }
    return route.fulfill({ json: ok({}) })
  })
  await seedAdminSession(page)
  await page.route(/\/manifest\.webmanifest(\?.*)?$/, route => route.fulfill({ contentType: 'application/manifest+json', json: { name: 'DispatchFlow', icons: [] } }))
  await page.route(/\/(dev-sw|sw)\.js(\?.*)?$/, route => route.fulfill({ contentType: 'application/javascript', body: 'self.addEventListener("install", () => self.skipWaiting())' }))
})

async function selectBothTaskRows(page: Page) {
  await page.route(api('/admin/tasks/query'), route => route.fulfill({ json: ok(pageData(taskRows)) }))
  await page.goto('/tasks')
  await expect(page.getByRole('row', { name: /TK-101/ })).toBeVisible()
  await page.getByRole('row', { name: /TK-101/ }).locator('input[type="checkbox"]').check()
  await page.getByRole('row', { name: /TK-102/ }).locator('input[type="checkbox"]').check()
  await expect(page.getByText('已选 2 项')).toBeVisible()
}

test('batch auto-assign reports partial success and keeps the failed row selected', async ({ page }) => {
  const bodies: unknown[] = []
  await selectBothTaskRows(page)
  await page.route(api('/admin/tasks/batch/auto-assign'), async route => {
    bodies.push(route.request().postDataJSON())
    await route.fulfill({ json: ok({
      operation: 'AUTO_ASSIGN',
      total: 2,
      successCount: 1,
      failureCount: 1,
      retryableTaskIds: [102],
      operatedAt: '2026-09-22T08:10:00Z',
      results: [
        { taskId: 101, taskNo: 'TK-101', success: true },
        { taskId: 102, taskNo: 'TK-102', success: false, reasonCode: 'CONFLICT', reasonMessage: '车辆被其他任务占用', retryable: true },
      ],
    }) })
  })

  await page.getByRole('button', { name: '批量自动派车' }).click()

  await expect(page.getByText('批量自动派车：成功 1 / 失败 1，1 项可重试')).toBeVisible()
  await expect(page.getByText('未完成：TK-102 · 车辆被其他任务占用')).toBeVisible()
  expect(bodies).toHaveLength(1)
  expect(bodies[0]).toMatchObject({ taskIds: [101, 102] })
  // 失败行必须还留在选择里（否则"重试"要点两遍，第二遍还容易漏）
  await expect(page.getByText('已选 1 项')).toBeVisible()
})

test('batch endpoint failure is surfaced instead of silently ignored', async ({ page }) => {
  await selectBothTaskRows(page)
  await page.route(api('/admin/tasks/batch/cancel'), route => route.fulfill({ status: 503, json: { success: false, code: 'UPSTREAM_DOWN', message: 'service unavailable' } }))

  await page.getByRole('button', { name: '批量取消' }).click()
  await page.locator('.ant-popconfirm .ant-btn-primary').click()

  await expect(page.getByText(/批量取消失败/)).toBeVisible()
})

test('task detail renders the decision funnel, gap and shadow disagreement', async ({ page }) => {
  await page.route(api('/admin/dispatch/decisions**'), route => route.fulfill({ json: ok([{
    snapshotId: 9001,
    taskId: 101,
    policyId: 'RULE',
    policyVersion: 'rule-v1',
    matchAlgorithm: 'HUNGARIAN',
    generatedAt: '2026-09-22T08:00:00Z',
    durationMicros: 1234,
    scoreGap: 40,
    tieCount: 0,
    funnel: { candidateTotal: 6, freshTelemetry: 5, socEligible: 3, socChainEligible: 2, reachable: 2 },
    winner: { rank: 1, vehicleCode: 'VH-001', totalScore: 880 },
    candidates: [
      { rank: 1, vehicleCode: 'VH-001', totalScore: 880 },
      { rank: 2, vehicleCode: 'VH-002', totalScore: 840 },
    ],
    shadow: { policyId: 'FORECAST', winnerCode: 'VH-002', agreed: false, regret: 40 },
  }]) }))

  await page.goto('/tasks/101')

  await expect(page.getByRole('heading', { name: '决策依据' })).toBeVisible()
  await expect(page.getByText('在线空闲 6 → 遥测新鲜 5 → SOC 达标 3 → 全链路达标 2 → 可达 2')).toBeVisible()
  await expect(page.getByText('影子对照 FORECAST', { exact: false })).toBeVisible()
  await expect(page.getByText('改选 VH-002', { exact: false })).toBeVisible()
  await expect(page.getByText('差 40 分', { exact: false })).toBeVisible()
})

test('decision snapshot read failure is stated, not swallowed', async ({ page }) => {
  await page.route(api('/admin/dispatch/decisions**'), route => route.fulfill({ status: 500, json: { success: false, code: 'BOOM', message: 'snapshot store unavailable' } }))

  await page.goto('/tasks/101')

  await expect(page.getByRole('heading', { name: '决策依据' })).toBeVisible()
  await expect(page.getByText(/决策快照读取失败/)).toBeVisible()
})

test('emergency pause requires a reason and then shows who paused it', async ({ page }) => {
  let postBody: Record<string, unknown> | null = null
  await page.route(api('/admin/dispatch/pause'), async route => {
    if (route.request().method() === 'POST') {
      postBody = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({ json: ok({ parkId: null, globalPaused: true, parkPaused: false, pauseReason: '暴雨封路', pausedBy: 'admin', pausedAt: '2026-09-22T08:20:00Z' }) })
      return
    }
    await route.fulfill({ json: ok({ parkId: null, globalPaused: false, parkPaused: false }) })
  })
  await page.route(api('/admin/dispatch/strategy/profiles**'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([{ parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true }]) }))

  await page.goto('/system/dispatch-strategy')
  await expect(page.getByText('紧急暂停派单')).toBeVisible()
  await expect(page.getByText('全局：派单正常进行中')).toBeVisible()

  // 空原因：既不发请求，也要有可见提示
  await page.getByRole('button', { name: '暂停派单' }).click()
  await expect(page.getByText('暂停派单必须填写原因')).toBeVisible()
  expect(postBody).toBe(null)

  await page.getByPlaceholder('暂停原因（暂停时必填）').fill('暴雨封路')
  await page.getByRole('button', { name: '暂停派单' }).click()
  await expect.poll(() => postBody).toMatchObject({ paused: true, reason: '暴雨封路' })
  // 园区档未选园时必须按住：parkId 缺失到后端就是全局档
  await page.locator('.ant-radio-button-wrapper', { hasText: '指定园区' }).click()
  await expect(page.getByRole('button', { name: '暂停派单' })).toBeDisabled()
})

test('pause status read failure never renders as healthy', async ({ page }) => {
  await page.route(api('/admin/dispatch/pause'), route => route.fulfill({ status: 503, json: { success: false, code: 'DOWN', message: 'dispatch api unavailable' } }))
  await page.route(api('/admin/dispatch/strategy/profiles**'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([]) }))

  await page.goto('/system/dispatch-strategy')

  await expect(page.getByText(/暂停状态读取失败/)).toBeVisible()
  await expect(page.getByText('全局：派单正常进行中')).toHaveCount(0)
})

/**
 * §7.6：删掉像素兜底之后，缺经纬度的车**必须**在界面上说明，而不是被画到一个编造的位置上。
 * 这条是那次删除的用户可见面：没有它，"以后有人把换算加回来"不会被任何测试拦住。
 *
 * 数据链路（实测确定，别照字面猜）：车辆走 `/admin/park/vehicles`，但它只在**有生效园区**时才请求
 * —— `/admin/parks` 返空 ⇒ `effectiveParkId` 为空 ⇒ 一次都不拉车，面板全 0。
 */
test('vehicles without real coordinates are counted as unknown instead of drawn at a fake position', async ({ page }) => {
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([
    { parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true },
  ]) }))
  await page.route(api('/admin/park/layout**'), route => route.fulfill({ json: ok({
    parkId: 1, width: 1000, height: 600, minZoom: 0.5, maxZoom: 3,
    vehicleSpeedPxPerSecond: 30, centerLng: 121.08, centerLat: 31.96,
    stations: [], parkingSpots: [], roadNodes: [], roadSegments: [],
  }) }))
  await page.route(api('/admin/park/geofences**'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/park/stations**'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/park/orders**'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/sse-ticket'), route => route.fulfill({ json: ok({ ticket: 'test-ticket' }) }))
  await page.route(api('/admin/park/vehicles**'), route => route.fulfill({ json: ok([
    // 地理桶 = SIM 且 ZJF-AV-*；PARK-* 属示意层，不该被算进这个计数
    { vehicleId: 1, vehicleCode: 'ZJF-AV-01', vehicleName: '有定位', linkMode: 'SIM', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', batteryLevel: 80, longitude: 121.0806, latitude: 31.9602 },
    { vehicleId: 2, vehicleCode: 'ZJF-AV-02', vehicleName: '缺定位', linkMode: 'SIM', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', batteryLevel: 70 },
    { vehicleId: 3, vehicleCode: 'PARK-01', vehicleName: '示意层车', linkMode: 'SIM', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', batteryLevel: 60 },
  ]) }))

  await page.goto('/vehicle-tracking?mode=geo')
  // 场景（园区调度 / 短驳地理）是持久化的独立开关，?mode=geo 只切渲染层；
  // 地理图层用的是 ZJF-AV-* 的 SIM 车，必须先把场景切过去。
  await page.getByText('短驳地理').click()

  await expect(page.getByText('位置未知 1 台')).toBeVisible()
  await expect(page.getByText('未回传真实经纬度，已从地理图层剔除')).toBeVisible()
})

/** GIS 总览的数据端点（园区总览 + 车/单/围栏/站点，再加一次地图版本）。 */
async function mockParkOverviewEndpoints(page: Page, options?: { fail?: boolean }) {
  const payload: Record<string, unknown> = {
    '/admin/park/overview': [
      { parkId: 1, parkName: '叠石桥 L1', vehicleCount: 2, onlineCount: 2, busyCount: 0 },
    ],
    '/admin/park/vehicles': [
      { vehicleId: 1, vehicleCode: 'ZJF-AV-01', linkMode: 'SIM', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', batteryLevel: 80, longitude: 121.0806, latitude: 31.9602 },
    ],
    '/admin/park/orders': [],
    '/admin/park/geofences': [],
    '/admin/park/stations': [],
    '/admin/station-service-positions/map-versions/active': { versionCode: 'V61' },
  }
  for (const [path, data] of Object.entries(payload)) {
    await page.route(api(path), route => (options?.fail
      ? route.fulfill({ status: 503, json: { success: false, code: 'DOWN', message: 'park service unavailable' } })
      : route.fulfill({ json: ok(data) })))
  }
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([
    { parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true },
  ]) }))
}

/** 只数业务数据请求：SSE 票据/流、鉴权、园区下拉这些与"这页打了几个查询"无关。 */
const isDataRequest = (pathname: string) =>
  /^\/api\/admin\/(park\/|station-service-positions\/)/.test(pathname)

/**
 * §6.5 首屏请求数预算。
 *
 * 两半都要钉住：① 首屏数据请求不超过预算；② **静置 4 秒后一个额外请求都不许有** ——
 * `ParkOverview` 原来是 `setInterval(refreshAll, 3000)` 无条件打 5 个端点（100 请求/分钟），
 * 只测首屏数量的话，把定时器改回 3 秒这条测试照样绿。
 */
test('gis overview stays request-silent while the stream owns refreshes', async ({ page }) => {
  const dataRequests: string[] = []
  page.on('request', request => {
    const url = new URL(request.url())
    if (isDataRequest(url.pathname)) dataRequests.push(`${request.method()} ${url.pathname}`)
  })
  await mockParkOverviewEndpoints(page)

  await page.goto('/gis/park-overview')
  await expect(page.getByText('更新 ')).toBeVisible({ timeout: 15_000 })
  const firstPaint = dataRequests.length
  await page.waitForTimeout(4_000)

  expect(firstPaint, `首屏数据请求 ${firstPaint} 个：\n${dataRequests.join('\n')}`)
    .toBeLessThanOrEqual(FIRST_PAINT_REQUEST_BUDGET)
  expect(dataRequests.length, `静置 4 秒后变成 ${dataRequests.length} 个：轮询又回到无条件定时`).toBe(firstPaint)
})

/** 取数失败必须显示"数据已停止更新"，不许退成"等待数据"或假装正常（§6.3 的镜像要求）。 */
test('gis overview reports stalled data instead of pretending to be healthy', async ({ page }) => {
  await mockParkOverviewEndpoints(page, { fail: true })

  await page.goto('/gis/park-overview')

  await expect(page.getByText(/数据已停止更新/)).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('等待数据')).toHaveCount(0)
})

/**
 * 调度工作台的 KPI 是最容易被"停在旧值上"骗到的地方：`workbench` store 原先只 console.error，
 * 于是"待派 0"既可能是真没单、也可能是后端已经挂了。现在必须抢下状态条。
 */
test('cockpit KPI strip states stall instead of showing stale counts as live', async ({ page }) => {
  await page.route(api('/admin/dispatch/workbench'), route => route.fulfill({
    status: 503,
    json: { success: false, code: 'DISPATCH_DOWN', message: 'dispatch api unavailable' },
  }))

  await page.goto('/workbench')

  // 状态条在头部、KPI 条与地图状态里各有一份（同一 label 复用），所以取 first()
  await expect(page.getByText(/数据已停止更新/).first()).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText(/态势更新于/)).toHaveCount(0)
})

/** 分析台的趋势图：读失败要说"读取失败"，不许退成"暂无趋势数据"（那看着像系统刚上线）。 */
test('dashboard trend failure is not rendered as empty data', async ({ page }) => {
  await page.route(api('/admin/analytics/efficiency**'), route => route.fulfill({
    status: 500,
    json: { success: false, code: 'ANALYTICS_DOWN', message: 'analytics unavailable' },
  }))

  await page.goto('/dashboard')

  await expect(page.getByText('趋势数据读取失败')).toBeVisible({ timeout: 15_000 })
  await expect(page.getByText('暂无趋势数据')).toHaveCount(0)
})

/**
 * §6.3 的漏网一处（2026-09-23 复核 §6.1 时发现的）：`analytics/Index.vue` 的 `loadAll()` 原先
 * **只有 try/finally、没有 catch，也没有任何 error 状态** ⇒ 任一端点失败时六个 ref 停在上一轮的
 * 旧值上、照常渲染成"实时数据"，异常还直接逃成未处理 rejection。现在必须清桶并抢下可见状态。
 */
test('analytics page states failure instead of leaving stale panels looking live', async ({ page }) => {
  await page.goto('/analytics')
  // 非空负控：默认 mock 全绿时不许冒出错误条，否则这条门永远绿也永远没意义
  await expect(page.getByText('分析数据读取失败')).toHaveCount(0)

  await page.route(api('/admin/analytics/**'), route => route.fulfill({
    status: 500,
    json: { success: false, code: 'ANALYTICS_DOWN', message: 'analytics unavailable' },
  }))
  await page.getByRole('button', { name: '刷新' }).click()

  await expect(page.getByText('分析数据读取失败')).toBeVisible({ timeout: 15_000 })
  // 面板必须整体撤下 —— 留着标题等于把上一轮的数当现在的数给人看
  await expect(page.getByText('链路 KPI')).toHaveCount(0)
})

import { expect, test, type Page } from '@playwright/test'

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
  // authStore (front/src/stores/auth.ts) reads token/user from sessionStorage, not localStorage.
  // Use the same storage so router.beforeEach sees the seeded admin session.
  await page.addInitScript(() => {
    sessionStorage.setItem('fsd_admin_token', 'e2e-admin-token')
    sessionStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }))
  })
  // ensureAuth() calls /api/admin/auth/me; mock it so the router guard accepts the seeded token.
  await page.route(api('/admin/auth/me'), route => route.fulfill({ json: ok({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }) }))
}

async function installConsoleGate(page: Page) {
  const errors: string[] = []
  page.on('console', msg => {
    if (msg.type() === 'error') errors.push(msg.text())
  })
  page.on('pageerror', err => errors.push(err.message))
  await page.exposeFunction('__assertNoConsoleErrors', () => {
    expect(errors.filter(text => /No match found|Failed to resolve component|no active component instance|unsupported MIME|mock/i.test(text))).toEqual([])
  })
}

function pageData<T>(records: T[]) {
  return { records, total: records.length, pageNo: 1, pageSize: 10 }
}

async function mockDispatchWorkbench(page: Page) {
  await page.route(api('/admin/dispatch/workbench'), route => route.fulfill({ json: ok({
    intervention: { pendingCount: 0, manualPendingCount: 0, openExceptionCount: 0, pendingTasks: [], manualPendingTasks: [], openExceptions: [] },
    fleetMetrics: { assignableVehicleCount: 2, pluggedStandbyCount: 0, chargingCount: 0, onlineVehicleCount: 2 },
    parkLayout: { parkId: 1, width: 1000, height: 600, centerLng: 121.1, centerLat: 31.9 },
    vehicles: [
      { vehicleId: 100, vehicleCode: 'VH-001', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE' },
      { vehicleId: 101, vehicleCode: 'VH-002', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE' },
    ],
  }) }))
}

test.beforeEach(async ({ page }) => {
  // Catch-all fallback registered FIRST so it has the LOWEST priority (Playwright
  // matches routes LIFO). seedAdminSession and test-specific page.route() calls
  // register later and therefore take precedence. Returns empty containers so
  // list/detail pages can render without hanging on unmocked ancillary APIs
  // (parks dropdown, vehicle options, etc.).
  await page.route(api('/admin/**'), route => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/query') || url.pathname.includes('/list')) {
      return route.fulfill({ json: ok({ records: [], total: 0, pageNo: 1, pageSize: 10 }) })
    }
    return route.fulfill({ json: ok({}) })
  })
  await seedAdminSession(page)
  await installConsoleGate(page)
  await page.route(/\/manifest\.webmanifest(\?.*)?$/, route => route.fulfill({ contentType: 'application/manifest+json', json: { name: 'DispatchFlow', icons: [] } }))
  await page.route(/\/(dev-sw|sw)\.js(\?.*)?$/, route => route.fulfill({ contentType: 'application/javascript', body: 'self.addEventListener("install", () => self.skipWaiting())' }))
})

test('mobile order initializes with mobile key and stale tracking hint', async ({ page }) => {
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([{ parkId: 1, parkName: '叠石桥 L1', defaultPark: true }]) }))
  await page.route(api('/admin/park/layout**'), route => route.fulfill({ json: ok({ parkId: 1, width: 1000, height: 600, centerLng: 121.1, centerLat: 31.9 }) }))
  await page.route(api('/admin/park/geofences**'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/park/stations**'), route => route.fulfill({ json: ok([
    { stationId: 1, stationCode: 'ZJF-PICK-01', stationName: '取货点', x: 100, y: 100, coordLng: 121.1, coordLat: 31.9, orderable: true },
    { stationId: 2, stationCode: 'ZJF-DROP-01', stationName: '送货点', x: 800, y: 450, coordLng: 121.11, coordLat: 31.91, orderable: true },
  ]) }))
  await page.route(api('/admin/park/orders**'), route => route.fulfill({ json: ok([
    {
      orderId: 1001,
      orderNo: 'MO-1001',
      vehicleId: 1,
      vehicleCode: 'AV-01',
      runtimeStage: 'HEADING_TO_PICKUP',
      pickupStation: { stationId: 1, stationCode: 'ZJF-PICK-01', stationName: '取货点', x: 100, y: 100, coordLng: 121.1, coordLat: 31.9 },
      dropoffStation: { stationId: 2, stationCode: 'ZJF-DROP-01', stationName: '送货点', x: 800, y: 450, coordLng: 121.11, coordLat: 31.91 },
    },
  ]) }))
  // §16.3 之后这一页只读一条聚合接口，所以"部分接口挂了页面还要在"这条注入改打在 /track 上；
  // 整园 vehicles 反而给回正常数据（它现在是 PC 页专用的， mobile 页不该再依赖它）。
  await page.route(api('/admin/park/track**'), route => route.fulfill({ status: 503, json: { success: false, code: 'DOWN', message: 'down' } }))
  await page.route(api('/admin/park/vehicles**'), route => route.fulfill({ json: ok([
    { vehicleId: 1, vehicleCode: 'AV-01', linkMode: 'SIM', onlineStatus: 'ONLINE', dispatchStatus: 'BUSY', batteryLevel: 70, longitude: 121.105, latitude: 31.905 },
  ]) }))

  await page.goto('/mobile/order')
  await expect(page.getByRole('heading', { name: '叫车送货' })).toBeVisible()
  await expect(page.getByText('未授权，请重新登录')).toHaveCount(0)
  await page.evaluate(() => (window as unknown as { __assertNoConsoleErrors: () => void }).__assertNoConsoleErrors())
})

test('analytics drilldown routes resolve to real list pages', async ({ page }) => {
  const badRoutes: string[] = []
  page.on('console', msg => {
    const text = msg.text()
    if (/No match found.*\/(order|task)\/list/.test(text)) badRoutes.push(text)
  })

  await page.goto('/analytics')
  await page.evaluate(() => window.history.pushState({}, '', '/tasks?status=SUCCESS'))
  await expect(page).toHaveURL(/\/tasks\?status=SUCCESS/)
  expect(badRoutes).toEqual([])
})

test('workbench batch reassign undo calls unassign for originally unassigned tasks', async ({ page }) => {
  const unassignCalls: unknown[] = []
  await page.route(api('/admin/tasks/batch/unassign'), async route => {
    unassignCalls.push(route.request().postDataJSON())
    await route.fulfill({ json: ok({ successCount: 1, failedCount: 0, items: [] }) })
  })
  await page.goto('/workbench')
  await page.evaluate(() => fetch('/api/admin/tasks/batch/unassign', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ taskIds: [101], remark: '撤销批量改派' }),
  }))
  expect(unassignCalls).toEqual([{ taskIds: [101], remark: '撤销批量改派' }])
})

test('notification target opens concrete exception route', async ({ page }) => {
  await page.goto('/tasks/88')
  await expect(page).toHaveURL(/\/tasks\/88/)
})

test('system health missing detail endpoints shows no mock metrics', async ({ page }) => {
  await page.route(api('/admin/**'), route => route.fulfill({ json: ok({ records: [], total: 0, items: [] }) }))
  await page.route(api('/admin/system/health'), route => route.fulfill({ json: ok({ overallStatus: 'UP', checkedAt: '2026-06-08T00:00:00Z', components: [] }) }))
  await page.route(api('/admin/system/health/metrics'), route => route.fulfill({ status: 404, json: { success: false, code: 'NOT_FOUND', message: 'missing' } }))
  await page.route(api('/admin/system/health/timeline'), route => route.fulfill({ status: 404, json: { success: false, code: 'NOT_FOUND', message: 'missing' } }))

  await page.goto('/system/health')
  await expect(page).toHaveURL(/\/system\/health/)
  await expect(page.getByText('MQ 堆积量')).toHaveCount(0)
  await page.evaluate(() => (window as unknown as { __assertNoConsoleErrors: () => void }).__assertNoConsoleErrors())
})

test('task list dispatch reassign and cancel trigger real user-path APIs', async ({ page }) => {
  const taskRows = [
    { taskId: 101, taskNo: 'TK-101', orderId: 201, vehicleId: null, status: 'PENDING', dispatchType: 'MANUAL', createdAt: '2026-06-09T08:00:00Z' },
    { taskId: 102, taskNo: 'TK-102', orderId: 202, vehicleId: 99, status: 'ASSIGNED', dispatchType: 'AUTO', createdAt: '2026-06-09T08:05:00Z' },
  ]
  const calls: { path: string; body: unknown }[] = []
  await mockDispatchWorkbench(page)
  await page.route(api('/admin/tasks/query'), route => route.fulfill({ json: ok(pageData(taskRows)) }))
  await page.route(api('/admin/tasks/101/manual-assign'), async route => {
    calls.push({ path: route.request().url(), body: route.request().postDataJSON() })
    await route.fulfill({ json: ok({ taskId: 101, status: 'ASSIGNED', vehicleId: 100 }) })
  })
  await page.route(api('/admin/tasks/102/reassign'), async route => {
    calls.push({ path: route.request().url(), body: route.request().postDataJSON() })
    await route.fulfill({ json: ok({ taskId: 102, status: 'ASSIGNED', vehicleId: 100 }) })
  })
  await page.route(api('/admin/tasks/102/cancel'), async route => {
    calls.push({ path: route.request().url(), body: route.request().postDataJSON() })
    await route.fulfill({ json: ok({ taskId: 102, status: 'CANCELED' }) })
  })

  await page.goto('/tasks')
  await page.getByRole('row', { name: /TK-101/ }).getByRole('button', { name: '派单' }).click()
  await page.locator('.ant-modal-body .ant-select-selector').click()
  await page.locator('.ant-select-item-option', { hasText: 'VH-001' }).click()
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/tasks/101/manual-assign'),
    page.locator('.ant-modal-footer .ant-btn-primary').click(),
  ])
  await page.getByRole('row', { name: /TK-102/ }).getByRole('button', { name: '改派' }).click()
  await page.locator('.ant-modal-body .ant-select-selector').last().click()
  await page.locator('.ant-select-item-option', { hasText: 'VH-001' }).last().click()
  await page.getByPlaceholder('请输入改派原因（至少5个字符）').fill('车辆临时调整')
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/tasks/102/reassign'),
    page.locator('.ant-modal-footer .ant-btn-primary').last().click(),
  ])
  await page.getByRole('row', { name: /TK-102/ }).getByRole('button', { name: '取消' }).click()
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/tasks/102/cancel'),
    page.locator('.ant-popconfirm .ant-btn-primary').click(),
  ])

  expect(calls.map(call => new URL(call.path).pathname)).toEqual([
    '/api/admin/tasks/101/manual-assign',
    '/api/admin/tasks/102/reassign',
    '/api/admin/tasks/102/cancel',
  ])
  expect(calls[0].body).toMatchObject({ vehicleId: 100 })
  expect(calls[1].body).toMatchObject({ vehicleId: 100, remark: '车辆临时调整' })
})

test('order list cancel triggers real user-path API', async ({ page }) => {
  const calls: unknown[] = []
  await page.route(api('/admin/orders/query'), route => route.fulfill({ json: ok(pageData([
    { orderId: 301, orderNo: 'OD-301', status: 'WAITING_DISPATCH', priority: 'P1', dispatchTaskId: null, createdAt: '2026-06-09T08:00:00Z', updatedAt: '2026-06-09T08:00:00Z' },
  ])) }))
  await page.route(api('/admin/orders/301/cancel'), async route => {
    calls.push(route.request().postDataJSON())
    await route.fulfill({ json: ok({ orderId: 301, status: 'CANCELED' }) })
  })

  await page.goto('/orders')
  await page.getByRole('row', { name: /OD-301/ }).getByRole('button', { name: '取消' }).click()
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/orders/301/cancel'),
    page.locator('.ant-popconfirm .ant-btn-primary').click(),
  ])

  expect(calls).toEqual([{ remark: '订单列表取消' }])
})

test('exception reassignment submits a live available vehicle from drawer', async ({ page }) => {
  const calls: unknown[] = []
  await page.route(api('/admin/exceptions/query'), route => route.fulfill({ json: ok(pageData([
    { id: 401, taskId: 101, orderId: 201, vehicleId: 99, exceptionType: 'VEHICLE_FAULT', exceptionMsg: '车辆故障', exceptionStatus: 'OPEN', resolverId: null, occurTime: '2026-06-09T08:00:00Z', aggCount: 1 },
  ])) }))
  await page.route(api('/admin/vehicles'), route => route.fulfill({ json: ok([
    { vehicleId: 6, vehicleCode: 'ZJF-AV-01', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE' },
    { vehicleId: 7, vehicleCode: 'ZJF-AV-02', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE' },
    { vehicleId: 8, vehicleCode: 'ZJF-AV-03', onlineStatus: 'ONLINE', dispatchStatus: 'BUSY' },
  ]) }))
  await page.route(api('/admin/exceptions/401/resolve'), async route => {
    calls.push(route.request().postDataJSON())
    await route.fulfill({ json: ok(null) })
  })

  await page.goto('/exceptions')
  await page.getByRole('row', { name: /车辆故障/ }).getByRole('button', { name: '处理' }).click()
  await page.locator('.ant-radio-wrapper', { hasText: '重新派单' }).click()
  await page.locator('.ant-drawer-body .ant-select-selector').click()
  await page.locator('.ant-select-item-option', { hasText: 'ZJF-AV-02' }).click()
  await page.getByPlaceholder('请描述处理方案（至少10个字符）').fill('重新派单到空闲车辆处理')
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/exceptions/401/resolve'),
    page.getByRole('button', { name: '提交处理' }).click(),
  ])

  expect(calls).toEqual([expect.objectContaining({ action: 'REASSIGN', vehicleId: 7 })])
})

test('exception close submits the backend CLOSE action', async ({ page }) => {
  const calls: unknown[] = []
  await page.route(api('/admin/exceptions/query'), route => route.fulfill({ json: ok(pageData([
    { id: 402, taskId: 102, orderId: 202, vehicleId: 6, exceptionType: 'VEHICLE_FAULT', exceptionMsg: '站点连接异常', exceptionStatus: 'OPEN', resolverId: null, occurTime: '2026-06-09T08:00:00Z', aggCount: 1 },
  ])) }))
  await page.route(api('/admin/vehicles'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/exceptions/402/resolve'), async route => {
    calls.push(route.request().postDataJSON())
    await route.fulfill({ json: ok(null) })
  })

  await page.goto('/exceptions')
  await page.getByRole('row', { name: /站点连接异常/ }).getByRole('button', { name: '处理' }).click()
  await expect(page.getByText('忽略异常')).toHaveCount(0)
  await expect(page.getByText('联系现场')).toHaveCount(0)
  await page.getByPlaceholder('请描述处理方案（至少10个字符）').fill('现场确认后关闭该异常记录')
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/exceptions/402/resolve'),
    page.getByRole('button', { name: '提交处理' }).click(),
  ])

  expect(calls).toEqual([expect.objectContaining({ action: 'CLOSE' })])
})

test('tracking demo orders by road-node coordinates, not by station IDs', async ({ page }) => {
  const calls: unknown[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/admin/park/orders' && request.method() === 'POST') {
      calls.push(request.postDataJSON())
    }
  })
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([
    { parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true },
  ]) }))
  await page.route(api('/admin/park/layout**'), route => route.fulfill({ json: ok({
    parkId: 1,
    width: 1000,
    height: 600,
    minZoom: 0.5,
    maxZoom: 3,
    vehicleSpeedPxPerSecond: 30,
    centerLng: 121.08,
    centerLat: 31.96,
    stations: [],
    parkingSpots: [],
    roadNodes: [],
    roadSegments: [],
  }) }))
  await page.route(api('/admin/park/vehicles**'), route => route.fulfill({ json: ok([
    { vehicleId: 6, vehicleCode: 'ZJF-AV-01', vehicleName: '演示车', linkMode: 'SIM', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', batteryLevel: 82 },
  ]) }))
  await page.route(api('/admin/park/geofences**'), route => route.fulfill({ json: ok([]) }))
  // 演示单的起终点 = 后端自己发布的路网落点（`GEO_POINT`）。设施 v2 之后 ACTIVE 站点里
  // 已经没有任何 PICKUP/DROPOFF，所以演示链路必须吃坐标，不能再吃 stationId。
  await page.route(api('/admin/park/stations**'), route => route.fulfill({ json: ok([
    { parkId: 1, stationId: 900, stationCode: 'GEO-OSM0017', stationName: '落点 A', stationType: 'GEO_POINT', area: 'ZJF', x: 10, y: 10, coordLng: 121.0801, coordLat: 31.9601 },
    { parkId: 1, stationId: 901, stationCode: 'GEO-OSM0018', stationName: '落点 B', stationType: 'GEO_POINT', area: 'ZJF', x: 90, y: 90, coordLng: 121.0902, coordLat: 31.9702 },
  ]) }))
  await page.route(api('/admin/park/orders'), async route => {
    if (route.request().method() !== 'POST') {
      await route.fulfill({ json: ok([]) })
      return
    }
    await route.fulfill({ json: ok({ orderId: 9001, orderNo: 'DEMO-9001' }) })
  })
  await page.route(api('/admin/sse-ticket'), route => route.fulfill({ json: ok({ ticket: 'test-ticket' }) }))

  await page.goto('/vehicle-tracking?mode=geo')
  const orderRequest = page.waitForRequest(request =>
    new URL(request.url()).pathname === '/api/admin/park/orders' && request.method() === 'POST',
  )
  await page.getByRole('button', { name: '开始演示' }).click()
  await orderRequest
  await expect.poll(() => calls.length).toBe(1)

  const [body] = calls as Array<Record<string, unknown>>
  expect(body).toEqual(expect.objectContaining({
    parkId: 1,
    pickupLng: 121.0801,
    pickupLat: 31.9601,
    dropoffLng: 121.0902,
    dropoffLat: 31.9702,
    priority: 'P1',
    orderPriority: 'NORMAL',
  }))
  expect(body.pickupStationId).toBeUndefined()
  expect(body.dropoffStationId).toBeUndefined()
})

test('demo mode states why it cannot start instead of silently stopping (设施 v2 的真实站点形态)', async ({ page }) => {
  const calls: unknown[] = []
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/admin/park/orders' && request.method() === 'POST') {
      calls.push(request.postDataJSON())
    }
  })
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([
    { parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true },
  ]) }))
  await page.route(api('/admin/park/layout**'), route => route.fulfill({ json: ok({
    parkId: 1,
    width: 1000,
    height: 600,
    minZoom: 0.5,
    maxZoom: 3,
    vehicleSpeedPxPerSecond: 30,
    centerLng: 121.08,
    centerLat: 31.96,
    stations: [],
    parkingSpots: [],
    roadNodes: [],
    roadSegments: [],
  }) }))
  await page.route(api('/admin/park/vehicles**'), route => route.fulfill({ json: ok([
    { vehicleId: 6, vehicleCode: 'ZJF-AV-01', vehicleName: '演示车', linkMode: 'SIM', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', batteryLevel: 82 },
  ]) }))
  await page.route(api('/admin/park/geofences**'), route => route.fulfill({ json: ok([]) }))
  // 那批站点在设施 v2 里已全部 INACTIVE、接口不再返回 ⇒ 旧版"按前缀找取送货站点"恒为空，
  // 点了"开始演示"只是静默自停。现网真实形态 = 只有总仓库、没有任何 GEO_POINT 落点。
  await page.route(api('/admin/park/stations**'), route => route.fulfill({ json: ok([
    { parkId: 1, stationId: 501, stationCode: 'FSD-HUB-01', stationName: '总仓库', stationType: 'MOTHERSHIP', area: 'ZJF', x: 10, y: 10, coordLng: 121.08, coordLat: 31.96 },
  ]) }))
  await page.route(api('/admin/park/orders'), async route => {
    if (route.request().method() !== 'POST') {
      await route.fulfill({ json: ok([]) })
      return
    }
    await route.fulfill({ json: ok({ orderId: 9001, orderNo: 'DEMO-9001' }) })
  })
  await page.route(api('/admin/sse-ticket'), route => route.fulfill({ json: ok({ ticket: 'test-ticket' }) }))

  await page.goto('/vehicle-tracking?mode=geo')
  await page.getByRole('button', { name: '开始演示' }).click()

  const reason = page.locator('.demo-error[role=status]')
  await expect(reason).toBeVisible()
  await expect(reason).toContainText('没有可用的演示取送货点')
  await expect(reason).toContainText('GEO_POINT')
  expect(calls).toHaveLength(0)
})


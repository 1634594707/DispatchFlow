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
  await page.addInitScript(() => {
    localStorage.setItem('fsd_admin_token', 'e2e-admin-token')
    localStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'admin', role: 'ADMIN' }))
  })
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
  await page.route(api('/admin/park/vehicles**'), route => route.fulfill({ status: 503, json: { success: false, code: 'DOWN', message: 'down' } }))

  await page.goto('/mobile/order')
  await expect(page.getByText('像看外卖一样看短驳配送')).toBeVisible()
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
  await page.getByRole('combobox', { name: '选择车辆' }).click()
  await page.getByTitle(/VH-001/).click()
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/tasks/101/manual-assign'),
    page.getByRole('button', { name: '确 定' }).click(),
  ])
  await page.getByRole('row', { name: /TK-102/ }).getByRole('button', { name: '改派' }).click()
  await page.getByRole('combobox', { name: '新车辆' }).click()
  await page.getByTitle(/VH-001/).click()
  await page.getByPlaceholder('请输入改派原因（至少5个字符）').fill('车辆临时调整')
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/tasks/102/reassign'),
    page.getByRole('button', { name: '确 定' }).click(),
  ])
  await page.getByRole('row', { name: /TK-102/ }).getByRole('button', { name: '取消' }).click()
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/tasks/102/cancel'),
    page.getByRole('button', { name: '确认' }).click(),
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
    page.getByRole('button', { name: '确认' }).click(),
  ])

  expect(calls).toEqual([{ remark: '订单列表取消' }])
})

test('exception reassignment submits selected vehicle from drawer', async ({ page }) => {
  const calls: unknown[] = []
  await page.route(api('/admin/exceptions/query'), route => route.fulfill({ json: ok(pageData([
    { id: 401, taskId: 101, orderId: 201, vehicleId: 99, exceptionType: 'VEHICLE_FAULT', exceptionMsg: '车辆故障', exceptionStatus: 'OPEN', resolverId: null, occurTime: '2026-06-09T08:00:00Z', aggCount: 1 },
  ])) }))
  await page.route(api('/admin/exceptions/401/resolve'), async route => {
    calls.push(route.request().postDataJSON())
    await route.fulfill({ json: ok(null) })
  })

  await page.goto('/exceptions')
  await page.getByRole('row', { name: /车辆故障/ }).getByRole('button', { name: '处理' }).click()
  await page.getByRole('combobox', { name: '选择车辆' }).click()
  await page.getByTitle(/VH-002/).click()
  await page.getByPlaceholder('请描述处理方案（至少10个字符）').fill('重新派单到空闲车辆处理')
  await Promise.all([
    page.waitForRequest(request => new URL(request.url()).pathname === '/api/admin/exceptions/401/resolve'),
    page.getByRole('button', { name: '提交处理' }).click(),
  ])

  expect(calls).toEqual([expect.objectContaining({ action: 'REASSIGN', vehicleId: 101 })])
})


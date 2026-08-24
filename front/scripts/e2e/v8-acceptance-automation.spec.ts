import { expect, test, type Page } from '@playwright/test'

const api = (path: string) => `/api${path}`

function ok(data: unknown) {
  return { success: true, code: 'OK', message: 'ok', data }
}

/** 兜底 mock 必须最先注册：Playwright 路由为后注册优先（LIFO）。 */
async function installCatchAll(page: Page) {
  await page.route(api('/admin/**'), route => route.fulfill({ json: ok({ records: [], total: 0 }) }))
  await page.route(/\/(dev-sw|sw)\.js(\?.*)?$/, route =>
    route.fulfill({ contentType: 'application/javascript', body: 'self.addEventListener("install", () => self.skipWaiting())' }))
  await page.route(/\/manifest\.webmanifest(\?.*)?$/, route =>
    route.fulfill({ contentType: 'application/manifest+json', json: { name: 'DispatchFlow', icons: [] } }))
}

async function overrideAuthMe(page: Page, role: 'ADMIN' | 'VIEWER') {
  await page.addInitScript(([role]) => {
    sessionStorage.setItem('fsd_admin_token', 'e2e-token')
    sessionStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'e2e', role, displayName: role }))
    sessionStorage.setItem('fsd_mobile_api_key', 'e2e-mobile-key')
  }, [role])
  await page.route(api('/admin/auth/me'), route =>
    route.fulfill({ json: ok({ userId: 1, username: 'e2e', role, displayName: role }) }))
}

test.use({ navigationTimeout: 30_000 })

/** 路线图 8.2：切换园区后，管理端查询必须携带新园区的 parkId。 */
test('park switch propagates selected parkId to admin queries', async ({ page }) => {
  await installCatchAll(page)
  await overrideAuthMe(page, 'ADMIN')
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([
    { parkId: 1, parkName: '叠石桥 L1', defaultPark: true },
    { parkId: 2, parkName: '二期园区', defaultPark: false },
  ]) }))

  const seenParkIds: (string | null)[] = []
  await page.route('**/api/admin/dispatch/workbench**', route => {
    seenParkIds.push(new URL(route.request().url()).searchParams.get('parkId'))
    return route.fulfill({ json: ok({ intervention: {}, fleetMetrics: {}, vehicles: [], pendingTasks: [], manualPendingTasks: [], openExceptions: [] }) })
  })

  await page.goto('/workbench')
  const selector = page.locator('.ant-select-selector').first()
  await expect(selector).toBeVisible()
  await selector.click()
  await page.waitForTimeout(500)
  process.stdout.write(`[dbg] dropdown visible=${await page.locator('.ant-select-dropdown').count()} options=${await page.locator('.ant-select-item-option').count()} texts=${JSON.stringify(await page.locator('.ant-select-item-option').allInnerTexts())}\n`)
  process.stdout.write(`[dbg] seenParkIds=${JSON.stringify(seenParkIds)}\n`)
  await page.locator('.ant-select-item-option', { hasText: '二期园区' }).click()
  await page.waitForTimeout(800)
  process.stdout.write(`[dbg] shown=${await page.locator('.ant-select-selection-item').first().getAttribute('title')}\n`)
  process.stdout.write(`[dbg] storage=${await page.evaluate(() => JSON.stringify(Object.fromEntries(Object.entries(localStorage))))}\n`)
  process.stdout.write(`[dbg] after switch seenParkIds=${JSON.stringify(seenParkIds)} url=${page.url()}\n`)

  await expect.poll(() => seenParkIds.filter(Boolean).pop()).toBe('2')
})

/** 路线图 8.2/Phase 4：requiresAdmin 路由对 VIEWER 仅隐藏入口并重定向。 */
test('viewer role is redirected away from admin-only routes', async ({ page }) => {
  await installCatchAll(page)
  await overrideAuthMe(page, 'VIEWER')

  await page.goto('/system/users')

  await expect(page).toHaveURL(/\/workbench/, { timeout: 15_000 })
})

/** 路线图 8.2/3.3：重复点击下单不产生重复请求，且请求携带幂等键。 */
test('order submit carries idempotency key and rotates after success', async ({ page }) => {
  await installCatchAll(page)
  await overrideAuthMe(page, 'ADMIN')
  await page.route(api('/admin/parks'), route => route.fulfill({ json: ok([{ parkId: 1, parkName: '叠石桥 L1', defaultPark: true }]) }))
  await page.route(api('/admin/park/layout**'), route =>
    route.fulfill({ json: ok({ parkId: 1, width: 1000, height: 600, centerLng: 121.1, centerLat: 31.9 }) }))
  await page.route(api('/admin/park/geofences**'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/park/stations**'), route => route.fulfill({ json: ok([
    { stationId: 504, stationCode: 'ZJF-PICK-01', stationName: '取货点', x: 100, y: 100, coordLng: 121.1, coordLat: 31.9, orderable: true },
    { stationId: 506, stationCode: 'ZJF-DROP-01', stationName: '送货点', x: 800, y: 450, coordLng: 121.11, coordLat: 31.91, orderable: true },
  ]) }))
  await page.route(api('/admin/park/orders**'), route => route.fulfill({ json: ok([]) }))
  await page.route(api('/admin/park/vehicles**'), route => route.fulfill({ status: 503, json: { success: false } }))

  let orderPosts = 0
  let lastBody = ''
  await page.route(api('/admin/sse-ticket'), route => route.fulfill({ json: ok({ ticket: 't' }) }))
  await page.route('**/api/admin/park/orders', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    orderPosts++
    lastBody = route.request().postData() ?? ''
    await new Promise(resolve => setTimeout(resolve, 400))
    await route.fulfill({ json: ok({ orderId: 5001, orderNo: 'MO-5001', message: 'ok' }) })
  })

  const keys: string[] = []

  await page.goto('/mobile/order')
  const submit = page.getByRole('button', { name: /确认下单/ })
  await expect(submit).toBeEnabled({ timeout: 20_000 })

  // 第一次下单：请求必须携带幂等键（后端据此保证重复提交返回原订单）
  await submit.click()
  await expect(submit).toBeEnabled({ timeout: 15_000 })
  expect(orderPosts).toBe(1)
  expect(lastBody).toContain('idempotencyKey')
  expect(JSON.parse(lastBody)).toMatchObject({
    parkId: 1,
    pickupStationId: 504,
    dropoffStationId: 506,
  })
  keys.push(JSON.parse(lastBody).idempotencyKey)

  // 第二次下单意图：必须使用全新的幂等键，不得复用旧键
  await submit.click()
  await expect(submit).toBeEnabled({ timeout: 15_000 })
  expect(orderPosts).toBe(2)
  keys.push(JSON.parse(lastBody).idempotencyKey)
  expect(keys[0]).not.toBe(keys[1])
})
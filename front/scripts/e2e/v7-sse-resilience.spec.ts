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
    sessionStorage.setItem('fsd_admin_token', 'e2e-admin-token')
    sessionStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }))
  })
  await page.route(api('/admin/auth/me'), route =>
    route.fulfill({ json: ok({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }) }))
}

async function installCatchAll(page: Page) {
  await page.route(api('/admin/**'), route => route.fulfill({ json: ok({ records: [], total: 0 }) }))
}

/** 路线图 8.1/8.2：SSE 断连后顶栏进入降级模式（假时钟加速 10 次退避重试）。 */
test('SSE stream failure drives header into degraded mode', async ({ page }) => {
  await seedAdminSession(page)
  await installCatchAll(page)
  await page.route(api('/admin/sse-ticket'), route =>
    route.fulfill({ json: ok({ ticket: 'e2e-ticket' }) }))
  // 阻断 SSE 流：每次连接尝试立即失败
  await page.route('**/api/admin/dispatch/stream**', route => route.abort())

  await page.clock.install()
  await page.goto('/workbench')

  const status = page.getByTestId('realtime-status')
  await expect(status).toBeVisible()
  // 初始未连接：不出现在线态
  await expect(status.locator('.stream-indicator.online')).toHaveCount(0)

  // 快进约 200s：烧完 10 次指数退避（1..30s 封顶）触发 onClose → 降级
  for (let i = 0; i < 20; i++) {
    await page.clock.runFor(15_000)
    await page.waitForTimeout(50)
  }

  await expect(status).toContainText('降级')
})

/** 路线图 8.2：SSE 恢复后自动回到实时模式。 */
test('SSE stream success returns header to live mode', async ({ page }) => {
  await seedAdminSession(page)
  await installCatchAll(page)
  await page.route(api('/admin/sse-ticket'), route =>
    route.fulfill({ json: ok({ ticket: 'e2e-ticket' }) }))
  // 以 text/event-stream 放行连接：EventSource 触发 onopen → markConnected
  await page.route('**/api/admin/dispatch/stream**', route =>
    route.fulfill({ status: 200, contentType: 'text/event-stream', body: 'data: {}\n\n' }))

  await page.goto('/workbench')

  const status = page.getByTestId('realtime-status')
  await expect(status).toBeVisible()
  await expect(status.locator('.stream-indicator.online')).toHaveCount(1, { timeout: 15_000 })
  await expect(status).not.toContainText('降级')
})
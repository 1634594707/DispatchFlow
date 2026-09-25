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

/**
 * 路线图 8.2：流开 ⇒ 实时态；流挂 ⇒ 降级态（两个方向都断言）。
 *
 * 这里必须换一个可控的 EventSource 双替身：`page.route` 的 `fulfill` 会把响应头、响应体
 * 一次性交给浏览器，EventSource 只能"开一下就立刻关闭"，在线窗口是毫秒级 —— 原来那条
 * 用例其实是靠 store 里多余的拆线抖动蹭到在线态的，一旦拆线次数收敛就再也抓不住。
 * 真实浏览器里的 SSE 行为由上一条用例（abort 真实请求）与 v12 的建连次数闸门负责。
 */
test('SSE stream success returns header to live mode', async ({ page }) => {
  await seedAdminSession(page)
  await installCatchAll(page)
  await page.route(api('/admin/sse-ticket'), route =>
    route.fulfill({ json: ok({ ticket: 'e2e-ticket' }) }))
  await page.addInitScript(() => {
    interface Openable {
      emitOpen(): void
      emitFail(): void
    }
    const instances: Openable[] = []
    class ControllableEventSource {
      static CONNECTING = 0
      static OPEN = 1
      static CLOSED = 2
      readyState = 0
      onopen: ((ev: unknown) => void) | null = null
      onerror: ((ev: unknown) => void) | null = null
      constructor() {
        instances.push(this)
      }
      addEventListener() {}
      removeEventListener() {}
      close() { this.readyState = 2 }
      emitOpen() {
        this.readyState = 1
        this.onopen?.({ type: 'open' })
      }
      emitFail() {
        this.readyState = 2
        this.onerror?.({ type: 'error' })
      }
    }
    Object.defineProperty(window, 'EventSource', { configurable: true, value: ControllableEventSource })
    Object.defineProperty(window, '__fsdEs', {
      configurable: true,
      get: () => instances[instances.length - 1] ?? null,
    })
  })

  await page.goto('/workbench')

  const status = page.getByTestId('realtime-status')
  await expect(status).toBeVisible()
  await expect(status).not.toContainText('降级')

  const emit = (method: 'emitOpen' | 'emitFail') => page.evaluate((name) => {
    const es = (window as unknown as { __fsdEs?: Record<string, () => void> }).__fsdEs
    if (!es) throw new Error('EventSource 尚未被创建')
    es[name]()
  }, method)

  // 换票请求回来之后客户端才会建流，先等替身就位再驱动状态
  await expect
    .poll(() => page.evaluate(() => Boolean((window as unknown as { __fsdEs?: unknown }).__fsdEs)))
    .toBe(true)

  await emit('emitOpen')
  await expect(status.locator('.stream-indicator.online')).toHaveCount(1)
  await expect(status).not.toContainText('降级')

  await emit('emitFail')
  await expect(status).toContainText('降级')
  await expect(status.locator('.stream-indicator.online')).toHaveCount(0)
})
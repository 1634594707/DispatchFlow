import { chromium, type FullConfig } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const SESSION_FILE = resolve(dirname(fileURLToPath(import.meta.url)), '.auth/session.json')

async function fetchAdminSession() {
  const apiBase = process.env.PERF_API_BASE || 'http://localhost:8080/api'
  const res = await fetch(`${apiBase}/admin/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'admin123' }),
  })
  if (!res.ok) {
    throw new Error(`login failed: HTTP ${res.status}`)
  }
  const json = await res.json()
  if (!json?.data?.token) {
    throw new Error(`login failed: ${JSON.stringify(json)}`)
  }
  return { token: json.data.token as string, user: json.data.user }
}

/**
 * 登录一次，把会话写进 `.auth/session.json`，并**真去开一页确认登录态有效**。
 *
 * <p>这里刻意不写 Playwright 的 storageState：它只带 cookies/localStorage，而应用的
 * `stores/auth.ts` 读 sessionStorage。校验留在 setup 里，是为了让"登录态失效"在启动阶段
 * 就炸，而不是让每条 perf 用例各自超时 30 s。</p>
 */
async function globalSetup(_config: FullConfig) {
  if (process.env.PLAYWRIGHT_SKIP_GLOBAL_AUTH === '1') return

  const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000'
  const session = await fetchAdminSession()
  const browser = await chromium.launch({ channel: 'chrome' })
  const context = await browser.newContext()

  await context.addInitScript(
    ([token, serializedUser]) => {
      sessionStorage.setItem('fsd_admin_token', token)
      sessionStorage.setItem('fsd_admin_user', serializedUser)
    },
    [session.token, JSON.stringify(session.user)] as [string, string],
  )

  const page = await context.newPage()
  await page.goto(`${baseURL}/workbench`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.fsd-layout', { timeout: 30_000 })

  mkdirSync(dirname(SESSION_FILE), { recursive: true })
  writeFileSync(SESSION_FILE, JSON.stringify({ ...session, baseURL }), 'utf8')
  await browser.close()
}

export default globalSetup

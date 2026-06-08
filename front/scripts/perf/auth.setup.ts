import { chromium, type FullConfig } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const AUTH_FILE = resolve(dirname(fileURLToPath(import.meta.url)), '.auth/admin.json')
const TOKEN_KEY = 'fsd_admin_token'
const USER_KEY = 'fsd_admin_user'

async function fetchAdminSession(baseURL: string) {
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
  return {
    token: json.data.token as string,
    user: json.data.user,
    baseURL,
  }
}

async function globalSetup(_config: FullConfig) {
  if (process.env.PLAYWRIGHT_SKIP_GLOBAL_AUTH === '1') return

  const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000'
  const session = await fetchAdminSession(baseURL)
  const browser = await chromium.launch({ channel: 'chrome' })
  const context = await browser.newContext()

  await context.addInitScript(
    ({ token, user, tokenKey, userKey }) => {
      localStorage.setItem(tokenKey, token)
      localStorage.setItem(userKey, JSON.stringify(user))
    },
    {
      token: session.token,
      user: session.user,
      tokenKey: TOKEN_KEY,
      userKey: USER_KEY,
    },
  )

  const page = await context.newPage()
  await page.goto(`${baseURL}/workbench`, { waitUntil: 'domcontentloaded' })
  await page.waitForSelector('.fsd-layout', { timeout: 30_000 })

  mkdirSync(dirname(AUTH_FILE), { recursive: true })
  await context.storageState({ path: AUTH_FILE })
  await browser.close()
}

export default globalSetup

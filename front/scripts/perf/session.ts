import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Page } from '@playwright/test'

const SESSION_FILE = resolve(dirname(fileURLToPath(import.meta.url)), '.auth/session.json')

/**
 * 登录态注入。
 *
 * <p>不能用 Playwright 的 `storageState`：它只保存 cookies 与 **localStorage**，
 * 而 `src/stores/auth.ts` 读写的是 **sessionStorage**（`fsd_admin_token` / `fsd_admin_user`）。
 * 之前 `auth.setup.ts` 往 localStorage 里塞，perf 套件因此从未真正登录过 —— 表现为
 * `page.waitForSelector('.fsd-layout')` 30 s 超时（页面被路由守卫弹回 /login）。</p>
 */
export async function seedPerfSession(page: Page) {
  const { token, user } = JSON.parse(readFileSync(SESSION_FILE, 'utf8')) as { token: string; user: unknown }
  await page.addInitScript(
    ([sessionToken, serializedUser]) => {
      sessionStorage.setItem('fsd_admin_token', sessionToken)
      sessionStorage.setItem('fsd_admin_user', serializedUser)
    },
    [token, JSON.stringify(user)] as [string, string],
  )
}

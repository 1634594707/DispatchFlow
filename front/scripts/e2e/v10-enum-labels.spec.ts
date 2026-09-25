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
    sessionStorage.setItem('fsd_admin_token', 'e2e-enum-token')
    sessionStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }))
  })
  await page.route(api('/admin/auth/me'), route => route.fulfill({ json: ok({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }) }))
}

test.describe('enum label fallback (§6.4)', () => {
  test.beforeEach(async ({ page }) => {
    await page.route(api('/admin/**'), route => {
      const url = new URL(route.request().url())
      if (url.pathname.endsWith('/query') || url.pathname.includes('/list')) {
        return route.fulfill({ json: ok({ records: [], total: 0, pageNo: 1, pageSize: 10 }) })
      }
      return route.fulfill({ json: ok({}) })
    })
    await seedAdminSession(page)
  })

  test('registered enums render Chinese and an unknown one stays visible but Chinese-led', async ({ page }) => {
    await page.route(api('/admin/tasks/query'), route => route.fulfill({ json: ok({
      records: [
        { taskId: 501, taskNo: 'TK-501', orderId: 601, vehicleId: null, status: 'ASSIGNED', dispatchType: 'AUTO', createdAt: '2026-06-09T08:00:00Z' },
        { taskId: 502, taskNo: 'TK-502', orderId: 602, vehicleId: null, status: 'QUANTUM_LEAP', dispatchType: 'AUTO', createdAt: '2026-06-09T08:05:00Z' },
      ],
      total: 2, pageNo: 1, pageSize: 10,
    }) }))

    await page.goto('/tasks')
    await expect(page.getByRole('row', { name: /TK-501/ })).toContainText('已派单')

    // 未登记的枚举不能只把英文印在中文界面上；但原值必须还在（否则对接方以为后端没问题）。
    const unknownRow = page.getByRole('row', { name: /TK-502/ })
    await expect(unknownRow).toContainText('未知状态(QUANTUM_LEAP)')
    await expect(unknownRow.getByText('QUANTUM_LEAP', { exact: true })).toHaveCount(0)
  })
})

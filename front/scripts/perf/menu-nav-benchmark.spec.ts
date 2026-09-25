import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
import { seedPerfSession } from './session'
import { NAVIGATION_TREE, NAV_PATH_MAP, filterNavByRole } from '../../src/config/navigation'
import type { NavItem } from '../../src/config/navigation'

type FlatNav = { label: string; path: string; parents: string[] }

function flattenNav(items = NAVIGATION_TREE, role: 'ADMIN' | 'OPERATOR' | 'VIEWER' | 'FIELD_OPS' = 'ADMIN', parents: string[] = []): FlatNav[] {
  const filtered = filterNavByRole(items, role)
  const result: FlatNav[] = []

  function walk(nodes: NavItem[], chain: string[]) {
    for (const node of nodes) {
      if (node.path) {
        result.push({ label: node.label, path: node.path, parents: chain })
      }
      if (node.children?.length) {
        walk(node.children, [...chain, node.label])
      }
    }
  }

  walk(filtered, parents)
  return result
}

/**
 * 侧栏是自定义导航（`.nav-group-header` + `.nav-item`），**不是** ant-design 的 `.ant-menu-*`；
 * 原实现按 ant 类名找分组与叶子项，命中数一直是 0，于是要么空转通过、要么 120 s 超时。
 * 现在按 `aria-expanded` 展开全部分组，再按文本点叶子项。
 */
async function openNavItem(page: Page, item: FlatNav) {
  // 侧栏被前面的用例留在收起态时，叶子项不渲染在文档流里
  if ((await page.locator('.fsd-sider').getAttribute('class') || '').includes('is-collapsed')) {
    await page.locator('.trigger-btn').first().click({ force: true })
    await page.waitForTimeout(300)
  }
  for (let pass = 0; pass < 3; pass += 1) {
    const closed = page.locator('.fsd-sider .nav-group-header[aria-expanded="false"]')
    const count = await closed.count()
    if (count === 0) break
    for (let i = 0; i < count; i += 1) {
      await closed.nth(0).click()
    }
  }
  await page.locator('.fsd-sider .nav-item').filter({ hasText: item.label }).first().click()
}

const LEAF_ROUTES = [...new Set(Object.values(NAV_PATH_MAP))]

test.beforeEach(async ({ page }) => {
  await seedPerfSession(page)
})

test.describe('navigation timing', () => {
  test('traverse leaf routes', async ({ page }) => {
    const timings: Array<{ route: string; ms: number }> = []

    for (const route of LEAF_ROUTES) {
      const started = Date.now()
      await page.goto(route, { waitUntil: 'domcontentloaded' })
      await expect(page.locator('.fsd-layout')).toBeVisible({ timeout: 30_000 })
      timings.push({ route, ms: Date.now() - started })
    }

    timings.sort((a, b) => b.ms - a.ms)
    console.table(timings)

    const avg = timings.reduce((sum, item) => sum + item.ms, 0) / timings.length
    // timings 是**降序**：p95 要取前 5% 位置，原来写成 `floor(len*0.95)` 取到的是尾部的最快值
    // （36 条路由时报出 p95=162ms < avg=224ms，一个"比平均值还好的 p95"本身就是红旗）
    const p95 = timings[Math.max(0, Math.ceil(timings.length * 0.05) - 1)]?.ms ?? 0
    console.log(`routes=${timings.length} avg=${avg.toFixed(0)}ms p95=${p95}ms slowest=${timings[0]?.ms ?? 0}ms`)

    // 预算：vite dev server 未压缩，取实测的量级放大到能吸收抖动但不放过退化的位置
    expect(p95, `路由渲染 p95=${p95}ms 超预算`).toBeLessThanOrEqual(2_500)
  })
})

test.describe('sidebar clicks', () => {
  test('menu click to content visible', async ({ page }) => {
    const navItems = flattenNav().slice(0, 15)
    const samples: number[] = []

    await page.goto('/workbench')
    await expect(page.locator('.fsd-layout')).toBeVisible()

    for (const item of navItems) {
      await page.goto('/workbench')
      await expect(page.locator('.fsd-layout')).toBeVisible()

      const started = Date.now()
      await openNavItem(page, item)
      await page.waitForURL(`**${item.path}**`, { timeout: 15_000 })
      await expect(page.locator('.fsd-content')).toBeVisible()
      samples.push(Date.now() - started)
    }

    expect(samples.length).toBeGreaterThan(0)

    // 每一条都必须真的点到：数量对不上就是"选择器没命中却在绿"的那种假绿
    expect(samples.length, '侧栏点击完成数与条目数不符（多半是导航没渲染或选择器失效）')
      .toBe(navItems.length)

    const avg = samples.reduce((a, b) => a + b, 0) / samples.length
    const sorted = [...samples].sort((a, b) => a - b)
    const p95 = sorted[Math.floor(sorted.length * 0.95)] ?? 0
    console.log(`samples=${samples.length} avg=${avg.toFixed(0)}ms p95=${p95}ms`)
    // 预算：实测 2026-09-22 本地 avg=285ms / p95=414ms（15 条，dev server）
    expect(p95, `侧栏点击到内容可见 p95=${p95}ms 超预算`).toBeLessThanOrEqual(1_500)
  })
})

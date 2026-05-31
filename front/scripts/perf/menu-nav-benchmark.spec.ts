import { test, expect } from '@playwright/test'
import type { Page } from '@playwright/test'
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

async function openNavItem(page: Page, item: FlatNav) {
  for (const parent of item.parents) {
    const title = page.locator('.fsd-sider .ant-menu-submenu').filter({ hasText: parent }).locator('.ant-menu-submenu-title').first()
    if (await title.count()) {
      const expanded = await title.evaluate((el) => el.parentElement?.classList.contains('ant-menu-submenu-open'))
      if (!expanded) {
        await title.click()
      }
    }
  }
  await page.locator('.fsd-sider .ant-menu-item').filter({ hasText: item.label }).first().click()
}

const LEAF_ROUTES = [...new Set(Object.values(NAV_PATH_MAP))]

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
    const p95 = timings[Math.floor(timings.length * 0.95)]?.ms ?? 0
    console.log(`routes=${timings.length} avg=${avg.toFixed(0)}ms p95=${p95}ms`)
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

    const avg = samples.reduce((a, b) => a + b, 0) / samples.length
    const sorted = [...samples].sort((a, b) => a - b)
    const p95 = sorted[Math.floor(sorted.length * 0.95)] ?? 0
    console.log(`samples=${samples.length} avg=${avg.toFixed(0)}ms p95=${p95}ms`)
  })
})

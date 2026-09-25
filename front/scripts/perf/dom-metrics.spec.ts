import { test, expect } from '@playwright/test'
import { seedPerfSession } from './session'

async function countDomNodes(page: import('@playwright/test').Page) {
  return page.evaluate(() => document.querySelectorAll('#app *').length)
}

/**
 * 侧栏是自定义导航（`SidebarContent.vue` 的 `.nav-group-header` / `.nav-item`），
 * **不是** ant-design 的 `.ant-menu-*`。原用例按 ant 类名选，命中数一直是 0 ⇒
 * `expandAllGroups` 什么都没点、`noneOpen` 量的只是一个空侧栏的 DOM，
 * 而 `expect(noneOpen).toBeGreaterThan(0)` 在这种退化页面上照样绿 —— 仪表本身假绿。
 * 现在：按 `aria-expanded` 展开/收起，并断言"确实展开过分组"，让退化变成红。
 */
async function expandAllGroups(page: import('@playwright/test').Page) {
  for (let pass = 0; pass < 3; pass += 1) {
    const closed = page.locator('.fsd-sider .nav-group-header[aria-expanded="false"]')
    const count = await closed.count()
    if (count === 0) break
    for (let i = 0; i < count; i += 1) {
      await closed.nth(0).click()
    }
  }
}

async function collapseAllGroups(page: import('@playwright/test').Page) {
  for (let pass = 0; pass < 3; pass += 1) {
    const open = page.locator('.fsd-sider .nav-group-header[aria-expanded="true"]')
    const count = await open.count()
    if (count === 0) break
    for (let i = 0; i < count; i += 1) {
      await open.nth(0).click()
    }
  }
}

test.describe('dom metrics', () => {
  test.beforeEach(async ({ page }) => {
    await seedPerfSession(page)
  })

  test('T5/T6 layout node counts', async ({ page }) => {
    await page.goto('/workbench')
    await expect(page.locator('.fsd-layout')).toBeVisible({ timeout: 30_000 })

    const sider = page.locator('.fsd-sider')
    const collapsedBtn = page.locator('.trigger-btn')

    // 先确认侧栏真的渲染了：这是防"仪表静默退化"的那道闸，比下面的任何数字都重要
    const groupCount = await sider.locator('.nav-group-header').count()
    const leafCount = await sider.locator('.nav-item').count()
    expect(groupCount, '侧栏分组没渲染，perf 数字将全部失真').toBeGreaterThan(0)
    expect(leafCount, '侧栏叶子项没渲染（多半是登录态没生效、导航按 null 角色过滤成空）').toBeGreaterThanOrEqual(10)

    const noneOpen = await countDomNodes(page)

    await collapsedBtn.click({ force: true })
    await page.waitForTimeout(300)
    const collapsed = await countDomNodes(page)

    await collapsedBtn.click({ force: true })
    await page.waitForTimeout(300)

    await expandAllGroups(page)
    await page.waitForTimeout(300)
    const allOpen = await countDomNodes(page)
    const scrollHeight = await sider.evaluate((el) => el.scrollHeight)
    const clientHeight = await sider.evaluate((el) => el.clientHeight)
    const hasScroll = scrollHeight > clientHeight

    await collapseAllGroups(page)

    console.log(JSON.stringify({
      navGroups: groupCount,
      navLeaves: leafCount,
      domNoneOpen: noneOpen,
      domAllOpen: allOpen,
      domCollapsed: collapsed,
      siderScrollHeight: scrollHeight,
      siderClientHeight: clientHeight,
      siderHasScroll: hasScroll,
    }))

    // 预算（2026-09-22 实测于本地全量数据：838 订单 / 20 台车 / 45 视图）：
    // 展开态节点数不得超过闭合态的 2 倍 —— 侧栏加层或整页常驻表格时这条会先响。
    expect(allOpen).toBeLessThan(noneOpen * 2)
    // 收起态必须真的更小，否则"折叠"只是视觉糊弄
    expect(collapsed).toBeLessThan(noneOpen)
    expect(noneOpen).toBeGreaterThan(0)
    expect(collapsed).toBeGreaterThan(0)
  })
})

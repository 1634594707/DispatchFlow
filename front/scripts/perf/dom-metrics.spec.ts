import { test, expect } from '@playwright/test'

async function countDomNodes(page: import('@playwright/test').Page) {
  return page.evaluate(() => document.querySelectorAll('#app *').length)
}

async function expandAllGroups(page: import('@playwright/test').Page) {
  const titles = page.locator('.fsd-sider .ant-menu-submenu-title')
  const count = await titles.count()
  for (let i = 0; i < count; i += 1) {
    const title = titles.nth(i)
    const expanded = await title.evaluate((el) => el.parentElement?.classList.contains('ant-menu-submenu-open'))
    if (!expanded) {
      await title.click()
    }
  }
}

async function collapseAllGroups(page: import('@playwright/test').Page) {
  const titles = page.locator('.fsd-sider .ant-menu-submenu-open > .ant-menu-submenu-title')
  const count = await titles.count()
  for (let i = count - 1; i >= 0; i -= 1) {
    await titles.nth(i).click()
  }
}

test.describe('dom metrics', () => {
  test('T5/T6 layout node counts', async ({ page }) => {
    await page.goto('/workbench')
    await expect(page.locator('.fsd-layout')).toBeVisible({ timeout: 30_000 })

    const sider = page.locator('.fsd-sider')
    const collapsedBtn = page.locator('.trigger-btn')

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
      domNoneOpen: noneOpen,
      domAllOpen: allOpen,
      domCollapsed: collapsed,
      siderScrollHeight: scrollHeight,
      siderClientHeight: clientHeight,
      siderHasScroll: hasScroll,
    }))

    expect(noneOpen).toBeGreaterThan(0)
    expect(collapsed).toBeGreaterThan(0)
  })
})

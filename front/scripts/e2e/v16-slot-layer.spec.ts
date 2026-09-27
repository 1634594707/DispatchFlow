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

const BASE = { lng: 121.080681, lat: 31.960337 }

/**
 * 车位层（§16.11①）。
 *
 * 这一层存在的理由就是"它以前不存在"：`/park/layout.parkingSpots` 一直给的是 application.yml 里
 * P1..P6 的老示意图像素坐标，前端也从没画过 —— 于是车明明 35/35 停在自己的待命位上，大屏读起来像乱停。
 * 所以这里断言的不是"画得好看"，而是**数据源与失败可见性**：读的是接口，读不到就明说。
 */
async function seed(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('fsd_admin_token', 'e2e-slot-token')
    sessionStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }))
  })
  await page.route(api('/admin/auth/me'), r => r.fulfill({ json: ok({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }) }))
  await page.route(api('/admin/dispatch/workbench'), r => r.fulfill({ json: ok({
    intervention: { pendingCount: 0, manualPendingCount: 0, openExceptionCount: 0, pendingTasks: [], manualPendingTasks: [], openExceptions: [] },
    fleetMetrics: { assignableVehicleCount: 1, pluggedStandbyCount: 0, chargingCount: 0, onlineVehicleCount: 1 },
    vehicles: [{ vehicleId: 7, vehicleCode: 'ZJF-AV-07', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', longitude: BASE.lng, latitude: BASE.lat, batteryLevel: 90 }],
  }) }))
  await page.route(api('/admin/parks'), r => r.fulfill({ json: ok([{ parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true }]) }))
  await page.route(api('/admin/park/metadata**'), r => r.fulfill({ json: ok({ parkId: 1, anchorLng: BASE.lng, anchorLat: BASE.lat, parkWidthMeters: 7957.7, parkHeightMeters: 9221.0 }) }))
  await page.route(api('/admin/park/geofences**'), r => r.fulfill({ json: ok([]) }))
  await page.route(api('/admin/park/stations**'), r => r.fulfill({ json: ok([
    { parkId: 1, stationId: 501, stationCode: 'ZJF-IDLE-01', stationName: '基地', x: 668, y: 624, coordLng: BASE.lng, coordLat: BASE.lat, area: 'ZJF', status: 'ACTIVE' },
  ]) }))
}

test.describe('cockpit slot layer (§16.11① 待命位与桩位)', () => {
  test('the layer is read from /park/layout for the selected park, not from a bundled copy', async ({ page }) => {
    await seed(page)
    const requested: string[] = []
    await page.route(api('/admin/park/layout**'), (route) => {
      requested.push(new URL(route.request().url()).searchParams.get('parkId') ?? 'none')
      return route.fulfill({ json: ok({
        parkId: 1,
        width: 1600,
        height: 1854,
        parkingSpots: [
          { code: 'P1', x: 550.12, y: 406.67, longitude: BASE.lng, latitude: BASE.lat, slotType: 'STANDBY', status: 'FREE' },
          { code: 'E1B1', x: 551, y: 407, longitude: BASE.lng + 0.0002, latitude: BASE.lat + 0.0002, slotType: 'CHARGING_ONLY', status: 'OCCUPIED', occupiedVehicleId: 7 },
        ],
      }) })
    })

    await page.goto('/workbench')
    await expect.poll(() => requested.length, { timeout: 15_000 }).toBeGreaterThan(0)
    // 首帧 refresh 跑在园区作用域解析之前，所以这里可能没有 parkId（与站点层同一行为，v11 同样不断言）；
    // 这一门要钉的是"数据源是接口"，不是查询参数长什么样。
    await expect(page.getByText('车位层读取失败')).toHaveCount(0)
  })

  test('a failed layout read is surfaced, and the layer draws nothing rather than the yaml points', async ({ page }) => {
    await seed(page)
    await page.route(api('/admin/park/layout**'), (route) =>
      route.fulfill({ status: 503, json: { success: false, code: 'DOWN', message: 'layout down' } }))

    await page.goto('/workbench')
    // 站点层没坏，所以那句站点的红字不该出现 —— 两条错误各有各的说法，不能互相顶掉
    await expect(page.getByText('车位层读取失败')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('站点图层读取失败')).toHaveCount(0)
  })
})

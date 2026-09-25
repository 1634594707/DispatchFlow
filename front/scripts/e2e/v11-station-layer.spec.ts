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
const stations = [
  { parkId: 1, stationId: 501, stationCode: 'ZJF-IDLE-01', stationName: '找家纺网基地', x: 668, y: 624, coordLng: BASE.lng, coordLat: BASE.lat, area: 'ZJF', deliveryZone: 'GEO_DELIVERY', status: 'ACTIVE' },
  { parkId: 1, stationId: 502, stationCode: 'ZJF-CHG-01', stationName: '基地充电点', x: 668, y: 624, coordLng: BASE.lng, coordLat: BASE.lat, area: 'ZJF', deliveryZone: 'GEO_DELIVERY', status: 'ACTIVE' },
  { parkId: 1, stationId: 503, stationCode: 'ZJF-PICK-01', stationName: '门市', x: 225, y: 694, coordLng: 121.074453, coordLat: 31.960396, area: 'ZJF', deliveryZone: 'GEO_DELIVERY', status: 'ACTIVE' },
  { parkId: 1, stationId: 504, stationCode: 'ZJF-CHG-02', stationName: '停用充电点', x: 466, y: 149, coordLng: 121.07978, coordLat: 31.963518, area: 'ZJF', deliveryZone: 'GEO_DELIVERY', status: 'INACTIVE' },
]

async function seed(page: Page) {
  await page.addInitScript(() => {
    sessionStorage.setItem('fsd_admin_token', 'e2e-station-token')
    sessionStorage.setItem('fsd_admin_user', JSON.stringify({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }))
  })
  await page.route(api('/admin/auth/me'), r => r.fulfill({ json: ok({ userId: 1, username: 'admin', role: 'ADMIN', displayName: 'admin' }) }))
  await page.route(api('/admin/dispatch/workbench'), r => r.fulfill({ json: ok({
    intervention: { pendingCount: 0, manualPendingCount: 0, openExceptionCount: 0, pendingTasks: [], manualPendingTasks: [], openExceptions: [] },
    fleetMetrics: { assignableVehicleCount: 1, pluggedStandbyCount: 0, chargingCount: 0, onlineVehicleCount: 1 },
    parkLayout: { parkId: 1, width: 1600, height: 1854, centerLng: 121.093236, centerLat: 31.937344 },
    vehicles: [{ vehicleId: 7, vehicleCode: 'ZJF-AV-07', onlineStatus: 'ONLINE', dispatchStatus: 'IDLE', longitude: BASE.lng, latitude: BASE.lat, batteryLevel: 90 }],
  }) }))
  await page.route(api('/admin/parks'), r => r.fulfill({ json: ok([{ parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true }]) }))
  await page.route(api('/admin/park/metadata**'), r => r.fulfill({ json: ok({ parkId: 1, anchorLng: 121.093236, anchorLat: 31.937344, parkWidthMeters: 7957.7, parkHeightMeters: 9221.0 }) }))
  await page.route(api('/admin/park/geofences**'), r => r.fulfill({ json: ok([]) }))
}

test.describe('cockpit station layer (§6.4 站点几何改读接口)', () => {
  test('station coordinates come from the API and the layer renders without a fallback copy', async ({ page }) => {
    await seed(page)
    const requested: string[] = []
    await page.route(api('/admin/park/stations**'), route => {
      requested.push(new URL(route.request().url()).searchParams.get('parkId') ?? 'none')
      return route.fulfill({ json: ok(stations) })
    })

    await page.goto('/workbench')
    // 等真实请求落地再判断：不 await 的话断言会在请求还在飞的时候就跑完（第一版就是这么假失败了一次）
    await expect.poll(() => requested.length, { timeout: 15_000 }).toBeGreaterThan(0)
    await expect(page.getByText('站点图层读取失败')).toHaveCount(0)
  })

  test('a failed station read is surfaced instead of silently drawing a stale subset', async ({ page }) => {
    await seed(page)
    await page.route(api('/admin/park/stations**'), route =>
      route.fulfill({ status: 503, json: { success: false, code: 'DOWN', message: 'stations down' } }),
    )

    await page.goto('/workbench')
    const notice = page.getByRole('status').filter({ hasText: '站点图层读取失败' })
    await expect(notice).toBeVisible()
    // 必须带原因（axios 抛错时是它自己的消息，不是 mock 里的 message 字段——这点断言写死，
    // 免得以后有人把 catch 改回"静默退回副本"却看不出来）。
    await expect(notice).toContainText(/Request failed with status code 503|HTTP 503/)
    // 关键判据：不能因为"有内置副本"就安静地画出一张少点的图
    await expect(page.getByText('待处理任务')).toBeVisible()
  })
})

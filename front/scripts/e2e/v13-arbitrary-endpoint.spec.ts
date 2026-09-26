import { expect, test, type Page } from '@playwright/test'

/**
 * W1 闸门（《服务范围与实路化任务路线图 2026-09-24》§1）：
 *  ① 范围内坐标能出单并派到车；
 *  ② 范围外坐标必须拿到 ORDER_ENDPOINT_OUT_OF_SERVICE_AREA，并且**页面上看得到原因**。
 *
 * 这里的"受理端"不是 canned response：mock 会真的去读收到的请求体，按坐标落在不在服务多边形里
 * 决定放行还是拒单。所以前端要是偷偷把坐标换成最近站点提交，两条断言都会红 ——
 * 这正是 §7"不在 W1 里让前端吸附失败就偷偷改派最近站点"要守的那条。
 */

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

function fail(code: string, message: string) {
  return { success: false, code, message, data: null }
}

const BASE = { lng: 121.080081, lat: 31.960592 }
const STATIONS = [
  { parkId: 1, stationId: 501, stationCode: 'ZJF-IDLE-01', stationName: '找家纺网基地', x: 668, y: 624, coordLng: BASE.lng, coordLat: BASE.lat, area: 'ZJF', status: 'ACTIVE' },
  { parkId: 1, stationId: 502, stationCode: 'ZJF-CHG-01', stationName: '基地充电点', x: 668, y: 624, coordLng: BASE.lng, coordLat: BASE.lat, area: 'ZJF', status: 'ACTIVE' },
  { parkId: 1, stationId: 503, stationCode: 'ZJF-PICK-01', stationName: '门市', x: 225, y: 694, coordLng: 121.074453, coordLat: 31.960396, area: 'ZJF', status: 'ACTIVE' },
  { parkId: 1, stationId: 504, stationCode: 'ZJF-DROP-01', stationName: '代发仓', x: 900, y: 500, coordLng: 121.086051, coordLat: 31.959812, area: 'ZJF', status: 'ACTIVE' },
  // V64 自动落点：不是人工作业点，不该出现在下单站点下拉里（§7）
  { parkId: 1, stationId: 505, stationCode: 'GEO-OSM0017', stationName: '送货·路网节点 OSM0017', stationType: 'GEO_POINT', x: 670, y: 626, coordLng: BASE.lng, coordLat: BASE.lat, area: 'ZJF', status: 'ACTIVE' },
]

/** 演示用的"可派单服务范围"并集近似：一个矩形。 */
const SERVICE_BOX = { minLng: 121.05, maxLng: 121.14, minLat: 31.88, maxLat: 31.98 }
const IN_AREA = { lng: 121.080354, lat: 31.961977 }
const OUT_OF_AREA = { lng: 121.55321, lat: 31.20987 }

function insideServiceBox(lng: number, lat: number) {
  return lng >= SERVICE_BOX.minLng && lng <= SERVICE_BOX.maxLng && lat >= SERVICE_BOX.minLat && lat <= SERVICE_BOX.maxLat
}

interface CapturedOrder {
  body: Record<string, unknown>
}

async function seedMobileOrderPage(page: Page): Promise<CapturedOrder[]> {
  const captured: CapturedOrder[] = []
  // GET /orders 只回真实受理成功的单 —— 否则"页面看得到单号"这条断言就成了白给的。
  const accepted: unknown[] = []
  let nextOrderId = 9001

  // 高德 JSAPI 走真实网络会在 CI 上飘；断掉它，顺带验证"地图打不开时仍能手输坐标"。
  await page.route(/\.amap\.com|amap-jsapi|webapi\.amap/, (route) => route.abort())

  await page.route(api('/admin/parks'), (r) =>
    r.fulfill({ json: ok([{ parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true }]) }),
  )
  await page.route(api('/admin/park/stations**'), (r) => r.fulfill({ json: ok(STATIONS) }))
  await page.route(api('/admin/park/layout**'), (r) =>
    r.fulfill({ json: ok({ enabled: true, parkId: 1, width: 1600, height: 1854, minZoom: 3, maxZoom: 20, vehicleSpeedPxPerSecond: 4, centerLng: BASE.lng, centerLat: BASE.lat, stations: [], parkingSpots: [], roadNodes: [], roadSegments: [] }) }),
  )
  await page.route(api('/admin/park/geofences**'), (r) =>
    r.fulfill({
      json: ok([
        { id: 1, parkId: 1, fenceCode: 'ZJF-ZONE-L1', fenceName: 'L1 商圈', fenceType: 'SERVICE_AREA', dispatchable: true, status: 'ACTIVE', polygon: [] },
      ]),
    }),
  )
  await page.route(api('/admin/park/geo/transform**'), (r) =>
    r.fulfill({ json: ok({ parkX: 668, parkY: 624, longitude: BASE.lng, latitude: BASE.lat }) }),
  )

  // 受理端：按收到的 payload 现场判定，不给固定答案。
  await page.route(api('/admin/park/orders'), (route) => {
    if (route.request().method() !== 'POST') {
      return route.fulfill({ json: ok(accepted) })
    }
    const body = route.request().postDataJSON() as Record<string, unknown>
    captured.push({ body })
    const dropoffLng = body.dropoffLng as number | undefined
    const dropoffLat = body.dropoffLat as number | undefined
    if (dropoffLng != null && dropoffLat != null && !insideServiceBox(dropoffLng, dropoffLat)) {
      return route.fulfill({
        json: fail(
          'ORDER_ENDPOINT_OUT_OF_SERVICE_AREA',
          `送货点 ${dropoffLng.toFixed(6)},${dropoffLat.toFixed(6)} 不在任何可派单服务范围内`,
        ),
      })
    }
    const orderId = nextOrderId++
    const order = {
      orderId,
      orderNo: `SO-${orderId}`,
      orderStatus: 'EXECUTING',
      taskId: orderId + 500,
      taskNo: `T-${orderId}`,
      taskStatus: 'ASSIGNED',
      vehicleId: 7,
      vehicleCode: 'ZJF-AV-07',
      vehicleName: '无人车 07',
      runtimeStage: 'HEADING_TO_PICKUP',
      pickupStation: STATIONS[2],
      dropoffStation: STATIONS[3],
      assignTime: null,
      startTime: null,
      finishTime: null,
      updatedAt: null,
    }
    accepted.push(order)
    return route.fulfill({
      json: ok({
        orderId,
        orderNo: `SO-${orderId}`,
        orderStatus: 'PENDING',
        taskId: orderId + 500,
        taskNo: `T-${orderId}`,
        taskStatus: 'ASSIGNED',
        vehicleId: 7,
        message: '订单已创建并派车',
        replayed: false,
      }),
    })
  })
  await page.route(api('/admin/park/vehicles**'), (route) =>
    route.fulfill({
      json: ok([
        { vehicleId: 7, vehicleCode: 'ZJF-AV-07', vehicleName: '无人车 07', onlineStatus: 'ONLINE', dispatchStatus: 'BUSY', currentTaskId: 9501, currentOrderId: 9001, batteryLevel: 82, x: 668, y: 624, longitude: BASE.lng, latitude: BASE.lat, runtimeStage: 'HEADING_TO_PICKUP', targetCode: null, targetType: null, charging: false, lowBattery: false, linkMode: 'SIM', trajectory: [], geoTrajectory: [], plannedRouteGeo: [] },
      ]),
    }),
  )

  /**
   * 移动追踪页现在只读这一条聚合接口（§16.3），不再轮询整园 orders/vehicles。
   * 上面的 `/admin/park/orders`（GET 分支）与 `/admin/park/vehicles` 两条 mock 是给 PC 页留的，
   * 这一页用不上它们 —— 别删，删了这条聚合 mock 就成了"永远返回同一份"的假一致性。
   * order 取最新受理的那一条，与页面下单后追踪的行为一致；车辆由 vehicleId 反查，
   * 两处数据因此不可能各说各话。
   */
  await page.route(api('/admin/park/track**'), (route) => {
    const order = accepted[accepted.length - 1] as
      | (Record<string, unknown> & { vehicleId: number; pickupStation: { stationCode?: string; area?: string }; dropoffStation: { stationCode?: string; area?: string } })
      | undefined
    const vehicle = [
      { vehicleId: 7, vehicleCode: 'ZJF-AV-07', vehicleName: '无人车 07', onlineStatus: 'ONLINE', dispatchStatus: 'BUSY', currentTaskId: 9501, currentOrderId: 9001, batteryLevel: 82, x: 668, y: 624, longitude: BASE.lng, latitude: BASE.lat, runtimeStage: 'HEADING_TO_PICKUP', targetCode: null, targetType: null, charging: false, lowBattery: false, linkMode: 'SIM', trajectory: [], geoTrajectory: [], plannedRouteGeo: [] },
    ].find((item) => item.vehicleId === order?.vehicleId)
    const recentOrders = accepted.map((item) => {
      const row = item as Record<string, unknown> & {
        pickupStation: { stationCode?: string; area?: string }
        dropoffStation: { stationCode?: string; area?: string }
      }
      return {
        orderId: row.orderId,
        orderNo: row.orderNo,
        orderStatus: row.orderStatus,
        runtimeStage: row.runtimeStage,
        vehicleId: row.vehicleId,
        pickupStationCode: row.pickupStation.stationCode ?? null,
        pickupStationArea: row.pickupStation.area ?? null,
        dropoffStationCode: row.dropoffStation.stationCode ?? null,
        dropoffStationArea: row.dropoffStation.area ?? null,
      }
    })
    return route.fulfill({ json: ok({ order: order ?? null, vehicle: vehicle ?? null, recentOrders, activeCount: recentOrders.length }) })
  })

  return captured
}

async function pickCoordinateEndpoint(page: Page, testId: string, lng: number, lat: number) {
  await page.getByTestId(`endpoint-${testId}-mode-coord`).click()
  await page.getByTestId(`endpoint-${testId}-lng`).fill(String(lng))
  await page.getByTestId(`endpoint-${testId}-lat`).fill(String(lat))
  await page.getByTestId(`endpoint-${testId}-apply`).click()
  await expect(page.getByTestId(`endpoint-${testId}-picked`)).toContainText(lng.toFixed(6))
}

test.describe('W1 任意点下单（坐标入口 + 拒单原因可见）', () => {
  test.beforeEach(async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('fsd_mobile_api_key', 'e2e-mobile-key')
    })
  })

  test('范围内的坐标能出单，并且提交的是坐标而不是最近站点', async ({ page }) => {
    const captured = await seedMobileOrderPage(page)
    await page.goto('/mobile/order')

    await pickCoordinateEndpoint(page, 'dropoff', IN_AREA.lng, IN_AREA.lat)
    // 取货端仍是默认服务点：二选一必须是"逐端"成立的
    await page.getByRole('button', { name: '确认下单' }).click()

    await expect
      .poll(() => captured.length, { timeout: 15_000 })
      .toBeGreaterThan(0)
    const body = captured[0].body
    expect(body.dropoffLng).toBeCloseTo(IN_AREA.lng, 4)
    expect(body.dropoffLat).toBeCloseTo(IN_AREA.lat, 4)
    expect(body).not.toHaveProperty('dropoffStationId')
    expect(body.pickupStationId).toBeTruthy()

    // 出单并且派到了车
    await expect(page.getByTestId('order-rejection')).toHaveCount(0)
    await expect(page.getByText('SO-9001')).toBeVisible()
    await expect(page.getByText('ZJF-AV-07').first()).toBeVisible()
  })

  test('范围外的坐标被拒，页面上看得到原因码而不是偷偷换站点重试', async ({ page }) => {
    const captured = await seedMobileOrderPage(page)
    await page.goto('/mobile/order')

    await pickCoordinateEndpoint(page, 'dropoff', OUT_OF_AREA.lng, OUT_OF_AREA.lat)
    await page.getByRole('button', { name: '确认下单' }).click()

    await expect.poll(() => captured.length, { timeout: 15_000 }).toBeGreaterThan(0)
    // 负控必须非空：重试时仍然发同一个坐标，绝不回落到站点
    for (const attempt of captured) {
      expect(attempt.body).not.toHaveProperty('dropoffStationId')
      expect(attempt.body.dropoffLng).toBeCloseTo(OUT_OF_AREA.lng, 4)
    }

    const note = page.getByTestId('order-rejection')
    await expect(note).toBeVisible()
    await expect(note).toHaveAttribute('data-code', 'ORDER_ENDPOINT_OUT_OF_SERVICE_AREA')
    await expect(note).toContainText('这个点不在服务范围内')
    await expect(note).toContainText('不在任何可派单服务范围内')

    await expect(page.getByText('SO-9001')).toHaveCount(0)
  })
})

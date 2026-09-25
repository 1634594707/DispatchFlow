import { expect, test, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

/**
 * §4 M2「手机端下单与展示补强」的闸门（T2-a ~ T2-f）。
 *
 * 分两层写：
 *  - 纯函数门：图层是纯计算，`buildGeofencePolygons` / `buildVehicleGeoMarkers` / 站点过滤直接在
 *    Node 里跑，不起浏览器 —— 这是 T2-b「可选参数只在移动端过滤、共用函数的默认行为不许变」最便宜的守法。
 *  - 页面门：marker 画在高德 canvas 上、DOM 数不到，所以 e2e 读追踪面板那行图层读数
 *    （`data-testid=tracking-map-legend` 的 data-*，值就是传给地图图层的 marker 数）。
 */

import {
  MARKER_BUDGET,
  MOBILE_SERVICE_FENCE_PREFIX,
  aggregateMarkersByPosition,
  buildGeofencePolygons,
  buildGeoPolylines,
  buildStationGeoMarkers,
  buildVehicleGeoMarkers,
  countVehiclesWithUnknownPosition,
} from '../../src/maps/parkGeoMapLayers'
import {
  buildGroupedMobileStationOptions,
  filterMobileOrderStations,
  mobileEnergyFacilityStations,
} from '../../src/maps/stationLayers'
import {
  ORDER_ENDPOINT_REJECT_LABELS,
  ORDER_REJECT_SERVICE_AREA_GUIDANCE,
  describeOrderRejection,
  needsServiceAreaGuidance,
} from '../../src/constants/orderEndpoints'
import { PILOT_VEHICLE_SPEC } from '../../src/constants/vehicleSpec'
import type { ParkGeofence, ParkOrderSnapshot, ParkStation, ParkVehicleSnapshot } from '../../src/types/park'

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

const BASE = { lng: 121.080681, lat: 31.960337 }

/** 本机只有 1 片参与受理判据的围栏（§0.2）。 */
const SERVICE_FENCE: ParkGeofence = {
  id: 11,
  parkId: 1,
  fenceCode: 'ZJF-ZONE-SVC-01',
  fenceName: '可下单服务范围',
  fenceType: 'BOUNDARY',
  scopeCode: 'L1_CORE',
  dispatchable: true,
  status: 'ACTIVE',
  polygon: [
    [121.05, 31.95],
    [121.11, 31.95],
    [121.11, 31.98],
    [121.05, 31.98],
  ],
}

/** `DEFAULT-BOUNDARY` 是展示包络：名字里已经写了"不参与受理判据"。 */
const DISPLAY_ENVELOPE: ParkGeofence = {
  ...SERVICE_FENCE,
  id: 12,
  fenceCode: 'DEFAULT-BOUNDARY',
  fenceName: '展示包络（不参与受理判据）',
  scopeCode: 'L1_CANDIDATE_ENVELOPE',
  dispatchable: false,
  polygon: [
    [121.04, 31.94],
    [121.12, 31.94],
    [121.12, 31.99],
    [121.04, 31.99],
  ],
}

const DISABLED_ZONES: ParkGeofence[] = [
  { ...SERVICE_FENCE, id: 13, fenceCode: 'ZJF-ZONE-L2', status: 'DISABLED' },
]

function vehicle(id: number, withPosition: boolean): ParkVehicleSnapshot {
  return {
    vehicleId: id,
    vehicleCode: `ZJF-AV-${String(id).padStart(2, '0')}`,
    vehicleName: `无人车 ${id}`,
    onlineStatus: 'ONLINE',
    dispatchStatus: id === 7 ? 'BUSY' : 'IDLE',
    currentTaskId: id === 7 ? 9501 : null,
    currentOrderId: id === 7 ? 9001 : null,
    batteryLevel: 70 + (id % 20),
    x: 600 + id,
    y: 600,
    longitude: withPosition ? BASE.lng + id * 0.0005 : null,
    latitude: withPosition ? BASE.lat + id * 0.0004 : null,
    runtimeStage: id === 7 ? 'HEADING_TO_PICKUP' : 'IDLE',
    targetCode: null,
    targetType: null,
    charging: false,
    lowBattery: false,
    linkMode: 'SIM',
    trajectory: [],
    geoTrajectory: [],
    plannedRouteGeo: [],
  } as unknown as ParkVehicleSnapshot
}

function station(
  stationId: number,
  stationCode: string,
  stationName: string,
  stationType: string,
  lng: number,
  lat: number,
): ParkStation {
  return {
    parkId: 1,
    stationId,
    stationCode,
    stationName,
    stationType,
    coordLng: lng,
    coordLat: lat,
    area: 'ZJF',
    status: 'ACTIVE',
  } as unknown as ParkStation
}

const HUB = station(501, 'FSD-HUB-01', '找家纺网总仓库', 'HUB', BASE.lng, BASE.lat)
const IDLE = station(502, 'ZJF-IDLE-01', '基地待命位', 'IDLE', BASE.lng, BASE.lat)
const AUTO_ENDPOINT = station(900, 'GEO-OSM0017', '送货·路网节点 OSM0017', 'GEO_POINT', BASE.lng, BASE.lat)

/** 本机设施形态（2026-09-25 实测活库 `GET /admin/park/stations?parkId=1`）：46 站全 ACTIVE
 *  = 35 个 `FSD-SWAP-*` 柜 + 6 根 `FSD-CHG-*` 桩（**桩与柜 01..06 同坐标**）+ 总仓库/待命位
 *  （MOTHERSHIP）+ 4 个 V64 自动落点。桩的编码是 `FSD-CHG-` 而不是 `ZJF-CHG-` ——
 *  `isEnergyFacilityStation()` 的前缀白名单里没有它，全靠 stationType 兜住，所以这里照抄真数据。 */
function localFacilityStations(): ParkStation[] {
  const stations: ParkStation[] = [HUB, IDLE]
  for (let i = 1; i <= 35; i++) {
    stations.push(
      station(700 + i, `FSD-SWAP-${String(i).padStart(2, '0')}`, `防爆换电柜 ${i}`, 'SWAP_CABINET', 121.06 + i * 0.0004, 31.95 + i * 0.0003),
    )
  }
  for (let i = 1; i <= 6; i++) {
    const co = stations[1 + i] // 与 FSD-SWAP-0i 同一个点（实测）
    stations.push(
      station(800 + i, `FSD-CHG-0${i}`, `基地充电桩 ${i}`, 'CHARGING_STATION', co.coordLng as number, co.coordLat as number),
    )
  }
  stations.push(AUTO_ENDPOINT)
  return stations
}

/** 桩各自占一个坐标的情形（用来验"只有桩"时这一层也画得出来）。 */
function spreadPileStations(): ParkStation[] {
  return localFacilityStations().map((item, index) =>
    item.stationType === 'CHARGING_STATION'
      ? ({ ...item, coordLng: 121.09 + index * 0.0005, coordLat: 31.965 } as ParkStation)
      : item,
  )
}

function facilityMarkers(stations: ParkStation[]) {
  return aggregateMarkersByPosition(
    buildStationGeoMarkers(
      mobileEnergyFacilityStations(stations).map((item) => ({
        station: item,
        id: `station-${item.stationId}`,
        label: `${item.stationName} · ${item.stationCode}`,
      })),
    ),
  )
}

const FORBIDDEN_ORDER_OPTION = /SWAP_CABINET|CHG-|FSD-SWAP-|GEO-/

// ───────────────────────────── 纯函数门（不起浏览器） ─────────────────────────────

test.describe('T2-a 追踪地图画全部车辆 + 高亮指派车', () => {
  const fleet = Array.from({ length: 20 }, (_, index) => vehicle(index + 1, true))

  test('20 台在图 ⇒ 20 个 marker，被指派那台是 selected', () => {
    const markers = buildVehicleGeoMarkers(fleet, { selectedId: 7 })
    expect(markers).toHaveLength(20)
    const selected = markers.filter((marker) => marker.selected)
    expect(selected).toHaveLength(1)
    expect(selected[0].id).toBe('7')
    // 只给指派车挂文字标签：20 个标签会把这张图糊掉，其余靠 hover 的 title
    expect(markers.filter((marker) => marker.showLabel)).toHaveLength(1)
  })

  test('标签带车号与 SOC，状态由图标带出', () => {
    const tracked = buildVehicleGeoMarkers(fleet, { selectedId: 7 }).find((marker) => marker.id === '7')
    expect(tracked?.label).toContain('ZJF-AV')
    expect(tracked?.label).toMatch(/·\s*7\d%/)
    expect(tracked?.markerType).toBe('vehicle')
    expect(tracked?.iconUrl).toContain('/icons/av-delivery-')
  })

  test('没有真经纬度的车不画点，但计入"位置未知"（§7.5 逐行契约）', () => {
    const withUnknown = [...fleet.slice(0, 18), vehicle(19, false), vehicle(20, false)]
    const markers = buildVehicleGeoMarkers(withUnknown, { selectedId: null })
    expect(markers).toHaveLength(18)
    // 不许拿像素 x/y（这里是 600+id）换算出一个假经纬度把点画上去
    for (const marker of markers) {
      expect(marker.position[0]).toBeGreaterThan(121)
      expect(marker.position[0]).toBeLessThan(122)
    }
    // 也不许这 2 台静默消失 —— 页面必须能把它们报出来
    expect(countVehiclesWithUnknownPosition(withUnknown)).toBe(2)
  })

  test('被追那单的 OD 连线画出来（includeOrderLines 已从 false 翻过来）', () => {
    const order: ParkOrderSnapshot = {
      orderId: 9001,
      runtimeStage: 'HEADING_TO_PICKUP',
      pickupStation: HUB,
      dropoffStation: AUTO_ENDPOINT,
    } as unknown as ParkOrderSnapshot
    const lines = buildGeoPolylines([fleet[6]], [order], {
      includeOrderLines: true,
      focusVehicleId: 7,
      focusOrderId: 9001,
    })
    const od = lines.find((line) => line.id === 'order-9001')
    expect(od?.path).toHaveLength(2)
    expect(od?.path[0]).toEqual([BASE.lng, BASE.lat])
    // 默认值仍是"画线"：共用这个函数的其它页面行为不许变
    const byDefault = buildGeoPolylines([fleet[6]], [order])
    expect(byDefault.find((line) => line.id === 'order-9001')).toBeDefined()
  })
})

test.describe('T2-b 移动端只画受理围栏', () => {
  const fences = [SERVICE_FENCE, DISPLAY_ENVELOPE, ...DISABLED_ZONES]

  test('默认行为不变：包络照旧画（PC 工作台在消费它），DISABLED 照旧不画', () => {
    const polygons = buildGeofencePolygons(fences)
    expect(polygons.map((polygon) => polygon.id)).toEqual(['11', '12'])
    const envelope = polygons.find((polygon) => polygon.id === '12')
    expect(envelope?.lineDash).toEqual([10, 8])
    expect(envelope?.strokeColor).toBe('#13C2C2')
  })

  test('移动端传前缀后只剩 1 条边界', () => {
    expect(MOBILE_SERVICE_FENCE_PREFIX).toBe('ZJF-ZONE-')
    const polygons = buildGeofencePolygons(fences, {
      fenceCodePrefix: MOBILE_SERVICE_FENCE_PREFIX,
    })
    expect(polygons.map((polygon) => polygon.id)).toEqual(['11'])
    expect(polygons[0].lineDash).toBeUndefined()
  })

  test('T2-f：描边闪一次只改样式，几何与可见围栏集合都不动', () => {
    const calm = buildGeofencePolygons(fences, { fenceCodePrefix: MOBILE_SERVICE_FENCE_PREFIX })
    const flashed = buildGeofencePolygons(fences, {
      fenceCodePrefix: MOBILE_SERVICE_FENCE_PREFIX,
      flashOutline: true,
    })
    expect(flashed.map((polygon) => polygon.id)).toEqual(calm.map((polygon) => polygon.id))
    expect(flashed[0].path).toEqual(calm[0].path)
    expect(flashed[0].strokeColor).toBe(calm[0].strokeColor)
    expect(flashed[0].strokeWeight ?? 0).toBeGreaterThan(calm[0].strokeWeight ?? 0)
  })
})

test.describe('T2-c 换电柜/充电桩图层上移动端', () => {
  const stations = localFacilityStations()
  const markers = facilityMarkers(stations)

  test('柜 marker 数 = 接口返回的 SWAP_CABINET 数（本机 35）', () => {
    expect(stations.filter((station) => station.stationType === 'SWAP_CABINET')).toHaveLength(35)
    const swap = markers.filter((marker) => marker.markerType === 'swap')
    expect(swap).toHaveLength(35)
    expect(swap.every((marker) => marker.iconUrl === '/icons/map-station-swap.svg')).toBe(true)
  })

  test('6 根与柜同坐标的基地桩收进柜徽标，点位不叠成墨点（§7.5）', () => {
    // 徽标代表是柜（`mobileEnergyFacilityStations` 把柜排在前），桩留在 `aggregatedLabels` 里：
    // 于是"柜 marker 数 = 接口柜数"这条闸门在本机数据下依然成立，而 6 根桩一个都没丢。
    expect(markers.filter((marker) => marker.markerType === 'charging')).toHaveLength(0)
    expect(markers).toHaveLength(35)
    const co = markers.find((marker) => marker.aggregatedCount === 2)
    expect(co?.aggregatedLabels).toEqual(['防爆换电柜 1 · FSD-SWAP-01', '基地充电桩 1 · FSD-CHG-01'])
    expect(co?.label).toContain('等 2 个点位')
    expect(co?.markerType).toBe('swap')
    // 桩各占一个坐标时（换布点就会这样），这一层单独也画得出来
    const spread = facilityMarkers(spreadPileStations()).filter((m) => m.markerType === 'charging')
    expect(spread).toHaveLength(6)
  })

  test('整页 marker 实测 57 个：三层全开仍在 `MARKER_BUDGET` 内', () => {
    const total = [
      ...markers,
      ...buildVehicleGeoMarkers(Array.from({ length: 20 }, (_, index) => vehicle(index + 1, true)), {
        selectedId: 7,
      }),
      ...buildStationGeoMarkers([
        { id: 'pickup', station: HUB },
        { id: 'dropoff', station: AUTO_ENDPOINT },
      ]),
    ]
    // 35 个补能点位（含 6 个"柜+桩"合并徽标）+ 20 台车 + 本单取送 2 = 57 ≤ 60。
    // 这条是"移动端把三层全开画不画得动"的实测口径：以后谁再往这页加层，先撞这条红。
    expect(total).toHaveLength(35 + 20 + 2)
    expect(total.length).toBeLessThanOrEqual(MARKER_BUDGET)
  })

  test('不变量：补能设施绝不进下单站点下拉（柜 / 桩 / GEO- 落点 零泄漏）', () => {
    const orderable = filterMobileOrderStations(stations)
    expect(orderable.map((item) => item.stationCode)).toEqual(['FSD-HUB-01'])
    const options = buildGroupedMobileStationOptions(orderable).flatMap((group) => group.options)
    expect(options).toHaveLength(1)
    for (const option of options) expect(option.label).not.toMatch(FORBIDDEN_ORDER_OPTION)
    for (const marker of facilityMarkers(stations)) {
      const id = Number(marker.id.replace('station-', ''))
      expect(orderable.some((item) => item.stationId === id)).toBe(false)
    }
  })
})

test.describe('T2-d/T2-e/T2-f 文案、轮询基准与拒单引导', () => {
  test('对外规格只说 X3 满载那档，且不许出现标称最长续航数字', () => {
    expect(PILOT_VEHICLE_SPEC).toBe('新石器 L4 · X3 满载续航 180 km · 30 s 快速换电 · 35 柜')
    expect(PILOT_VEHICLE_SPEC).not.toMatch(/200/)
  })

  test('追踪轮询基准已是具名 1500 ms，退避封顶 30 s 未动', () => {
    const source = readFileSync(
      fileURLToPath(new URL('../../src/views/mobile/ParkOrder.vue', import.meta.url)),
      'utf8',
    )
    expect(source).toMatch(/const TRACKING_POLL_BASE_MS = 1500/)
    expect(source).toMatch(
      /Math\.min\(TRACKING_POLL_BASE_MS \* 2 \*\* trackingFailureCount\.value, 30000\)/,
    )
    expect(source).not.toMatch(/Math\.min\(3000 \* 2/)
  })

  test('吸附失败/围栏外两类给引导，其余原因码不给', () => {
    for (const code of ['ORDER_ENDPOINT_OUT_OF_SERVICE_AREA', 'ORDER_ENDPOINT_SNAP_FAILED']) {
      expect(needsServiceAreaGuidance(code)).toBe(true)
      const rejection = describeOrderRejection({ code, message: '后端原文' })
      expect(rejection.guidance).toBe(ORDER_REJECT_SERVICE_AREA_GUIDANCE)
      // 标题原文不许被改走样：v13 的两条闸门按它断言
      expect(rejection.headline).toBe(ORDER_ENDPOINT_REJECT_LABELS[code])
    }
    for (const code of [
      'ORDER_ENDPOINT_UNREACHABLE',
      'ORDER_ROAD_GRAPH_EMPTY',
      'ORDER_CREATE_FAILED',
    ]) {
      expect(needsServiceAreaGuidance(code)).toBe(false)
      expect(describeOrderRejection({ code, message: '后端原文' }).guidance).toBeUndefined()
    }
  })
})

// ───────────────────────────── 页面门（需要 dev server） ─────────────────────────────

async function seedMobilePage(page: Page) {
  // 高德 JSAPI 走真实网络在本机会飘；图层读数读的是传给图层的 marker，不依赖 canvas。
  await page.route(/\.amap\.com|amap-jsapi|webapi\.amap/, (route) => route.abort())
  await page.route(api('/admin/parks'), (route) =>
    route.fulfill({
      json: ok([{ parkId: 1, parkCode: 'ZJF', parkName: '叠石桥 L1', defaultPark: true }]),
    }),
  )
  await page.route(api('/admin/park/stations**'), (route) =>
    route.fulfill({ json: ok(localFacilityStations()) }),
  )
  await page.route(api('/admin/park/layout**'), (route) =>
    route.fulfill({
      json: ok({
        enabled: true,
        parkId: 1,
        width: 1600,
        height: 1854,
        centerLng: BASE.lng,
        centerLat: BASE.lat,
        stations: [],
        parkingSpots: [],
        roadNodes: [],
        roadSegments: [],
      }),
    }),
  )
  await page.route(api('/admin/park/geofences**'), (route) =>
    route.fulfill({ json: ok([SERVICE_FENCE, DISPLAY_ENVELOPE, ...DISABLED_ZONES]) }),
  )
  await page.route(api('/admin/park/orders**'), (route) => {
    if (route.request().method() !== 'GET') return route.fallback()
    return route.fulfill({
      json: ok([
        {
          orderId: 9001,
          orderNo: 'SO-9001',
          orderStatus: 'EXECUTING',
          taskId: 9501,
          taskNo: 'T-9501',
          taskStatus: 'ASSIGNED',
          vehicleId: 7,
          vehicleCode: 'ZJF-AV-07',
          vehicleName: '无人车 07',
          runtimeStage: 'HEADING_TO_PICKUP',
          pickupStation: HUB,
          dropoffStation: AUTO_ENDPOINT,
        },
      ]),
    })
  })
  // 12 台有真经纬度 + 1 台没有：后者必须画不出、但要报出来
  const vehicles = [
    ...Array.from({ length: 12 }, (_, index) => vehicle(index + 1, true)),
    vehicle(13, false),
  ]
  await page.route(api('/admin/park/vehicles**'), (route) => route.fulfill({ json: ok(vehicles) }))
  await page.route(api('/admin/park/geo/transform**'), (route) =>
    route.fulfill({ json: ok({ parkX: 668, parkY: 624, longitude: BASE.lng, latitude: BASE.lat }) }),
  )
  return vehicles
}

test.describe('移动端追踪地图页面门（T2-a/T2-c/T2-f）', () => {
  test('全部车辆进图层、位置未知的另算，柜/桩按接口返回画', async ({ page }) => {
    await seedMobilePage(page)
    await page.goto('/mobile/order')

    const legend = page.getByTestId('tracking-map-legend')
    await expect(legend).toBeVisible()
    await expect(legend).toHaveAttribute('data-vehicle-markers', '12')
    await expect(legend).toHaveAttribute('data-position-unknown', '1')
    await expect(legend).toHaveAttribute('data-swap-markers', '35')
    // 6 根基地桩与柜 01..06 同坐标 ⇒ 收进柜徽标，所以独立桩徽标是 0、补能点位共 35 处
    await expect(legend).toHaveAttribute('data-charging-markers', '0')
    await expect(legend).toHaveAttribute('data-facility-points', '35')
    await expect(legend).toContainText('1 台位置未知')
    await expect(legend).toContainText('补能点 35 处')
    // T2-d：对外规格文案就在地图下面那行，演示时不用翻页
    await expect(page.getByTestId('vehicle-spec')).toContainText('X3 满载续航 180 km')
  })

  test('下单站点下拉里零个补能设施与自动落点', async ({ page }) => {
    await seedMobilePage(page)
    await page.goto('/mobile/order')

    await page.getByTestId('endpoint-pickup-mode-station').click()
    await page.getByTestId('endpoint-pickup-station').click()
    const options = page.locator('.mobile-order-select-dropdown .ant-select-item-option')
    await expect(options).toHaveCount(1)
    await expect(options.filter({ hasText: FORBIDDEN_ORDER_OPTION })).toHaveCount(0)
    await expect(options.first()).toContainText('FSD-HUB-01')
  })

  test('围栏外拒单追加"在高亮服务范围内选点"的引导，常驻元素不变', async ({ page }) => {
    await seedMobilePage(page)
    await page.route(api('/admin/park/orders'), (route) => {
      if (route.request().method() !== 'POST') return route.fallback()
      return route.fulfill({
        json: fail('ORDER_ENDPOINT_OUT_OF_SERVICE_AREA', '送货点不在任何可派单服务范围内'),
      })
    })
    await page.goto('/mobile/order')

    await page.getByTestId('endpoint-dropoff-mode-coord').click()
    await page.getByTestId('endpoint-dropoff-lng').fill('121.553210')
    await page.getByTestId('endpoint-dropoff-lat').fill('31.209870')
    await page.getByTestId('endpoint-dropoff-apply').click()
    await page.getByRole('button', { name: '确认下单' }).click()

    const note = page.getByTestId('order-rejection')
    await expect(note).toBeVisible()
    await expect(note).toHaveAttribute('data-code', 'ORDER_ENDPOINT_OUT_OF_SERVICE_AREA')
    // v13 的两条断言口径：标题原文 + 后端原文，引导语是追加不是替换
    await expect(note).toContainText('这个点不在服务范围内')
    await expect(note).toContainText('不在任何可派单服务范围内')
    await expect(page.getByTestId('order-rejection-guidance')).toContainText(
      '请在高亮的服务范围内选点',
    )

    // 围栏描边闪一次：拒单后 900 ms 内是 on，然后自己落回 off —— 一次性，不许循环闪
    const legend = page.getByTestId('tracking-map-legend')
    await expect(legend).toHaveAttribute('data-fence-flash', 'on')
    await expect(legend).toHaveAttribute('data-fence-flash', 'off', { timeout: 5_000 })
  })
})

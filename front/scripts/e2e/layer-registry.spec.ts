import { expect, test } from '@playwright/test'

// 图层注册表的纯函数门：不打开浏览器、不打后端，只验"同一份图层实现"的判据。
// 为什么放这儿：front/ 没有 vitest（`src/**` 里 0 个 spec），而 §6.3/§6.5 的阈值与聚合
// 是纯计算 —— 用已装的 @playwright/test 当 runner 比新增依赖更划算。
import {
  L0_COVERAGE_CIRCLES,
  MAP_SCALE_TIERS,
  MARKER_BUDGET,
  aggregateMarkersByPosition,
  basePositionFromStations,
  buildOperationalStationMarkers,
  shouldAggregateMarkers,
  splitVehiclesByBasePresence,
} from '../../src/maps/parkGeoMapLayers'
import { ZJF_L0_COVERAGE } from '../../src/maps/zjfPilotGeo'
import type { GeoMapMarker } from '../../src/maps/types'
import type { ParkStation, ParkVehicleSnapshot } from '../../src/types/park'

function marker(id: string, lng: number, lat: number, extra: Partial<GeoMapMarker> = {}): GeoMapMarker {
  return { id, position: [lng, lat], label: id, ...extra }
}

test.describe('map layer registry (§6.3 / §6.5)', () => {
  test('collapses markers sharing a coordinate into one counted badge', () => {
    // 基地真实重叠：ZJF-CHG-01 与 ZJF-IDLE-01 在数据层就是同一个点（节点 OSM0009）。
    const out = aggregateMarkersByPosition([
      marker('base-chg', 121.080681, 31.960337),
      marker('base-idle', 121.080681, 31.960337),
      marker('other', 121.074453, 31.960396),
    ])
    expect(out).toHaveLength(2)
    const badge = out.find((m) => m.aggregatedCount)
    expect(badge?.aggregatedCount).toBe(2)
    expect(badge?.label).toContain('等 2 个点位')
    expect(badge?.aggregatedLabels).toEqual(['base-chg', 'base-idle'])
    expect(badge?.showLabel).toBe(true)
    // 不重叠的那个必须原样保留，别把计数逻辑做成"什么都合并"。
    expect(out.find((m) => m.id === 'other')?.aggregatedCount).toBeUndefined()
  })

  test('keeps distinct positions one-to-one', () => {
    const out = aggregateMarkersByPosition([
      marker('a', 121.07, 31.96),
      marker('b', 121.08, 31.96),
      marker('c', 121.09, 31.96),
    ])
    expect(out.map((m) => m.id)).toEqual(['a', 'b', 'c'])
  })

  test('selection survives aggregation', () => {
    // 叠在下面那个被选中时，徽标也得是选中态，否则"点了没反应"。
    const out = aggregateMarkersByPosition([
      marker('front', 121.080681, 31.960337),
      marker('behind', 121.080681, 31.960337, { selected: true }),
    ])
    expect(out).toHaveLength(1)
    expect(out[0].selected).toBe(true)
    expect(out[0].id).toBe('front')
  })

  test('aggregation threshold follows the fleet scale tier', () => {
    expect(MAP_SCALE_TIERS).toEqual({ M: 20, L: 40 })
    expect(shouldAggregateMarkers(MAP_SCALE_TIERS.M)).toBe(false)
    expect(shouldAggregateMarkers(MAP_SCALE_TIERS.L - 1)).toBe(false)
    expect(shouldAggregateMarkers(MAP_SCALE_TIERS.L)).toBe(true)
    expect(MARKER_BUDGET).toBeGreaterThanOrEqual(MAP_SCALE_TIERS.L)
  })

  test('station layer collapses only the base overlap, not the whole set', () => {
    const stations = [
      { stationId: 1, stationCode: 'ZJF-PICK-01', stationName: '门市', coordLng: 121.074453, coordLat: 31.960396 },
      { stationId: 2, stationCode: 'ZJF-IDLE-01', stationName: '基地', coordLng: 121.080681, coordLat: 31.960337 },
      { stationId: 3, stationCode: 'ZJF-CHG-01', stationName: '充电', coordLng: 121.080681, coordLat: 31.960337 },
    ] as unknown as ParkStation[]
    const out = buildOperationalStationMarkers(stations)
    expect(out).toHaveLength(2)
    const badge = out.find((m) => m.aggregatedCount)
    // 代表取组内第一个（IDLE-01），点徽标进的是"基地"而不是某个桩。
    expect(badge?.id).toBe('station-2')
    expect(badge?.aggregatedCount).toBe(2)
    expect(badge?.markerType).toBe('idle')
  })

  test('base point comes from the IDLE-01 station, not the canvas anchor', () => {
    // 实测过的坑：`t_park.anchor_lng/lat` 是 schematic **画布锚点**（DEFAULT 园区 121.093236,31.937344，
    // 2026-09-23 随走廊换图重定标），与基地点 121.080681,31.960337 差约 2.8 km。拿它当基地，
    // 75 m 半径内一台车都不算"在场"，而且不会报错——只是数字永远是 0。这条断言把两个列的语义钉死。
    const CANVAS_ANCHOR: [number, number] = [121.093236, 31.937344]
    const stations = [
      { stationId: 1, stationCode: 'ZJF-IDLE-01', stationName: '基地', coordLng: 121.080681, coordLat: 31.960337 },
      { stationId: 2, stationCode: 'ZJF-PICK-01', stationName: '门市', coordLng: 121.074453, coordLat: 31.960396 },
    ] as unknown as ParkStation[]
    const base = basePositionFromStations(stations)
    expect(base).toEqual([121.080681, 31.960337])
    const vehicle = { vehicleId: 7, longitude: 121.080681, latitude: 31.960337 } as unknown as ParkVehicleSnapshot

    expect(splitVehiclesByBasePresence([vehicle], base).atBase).toHaveLength(1)
    // 用画布锚点当基地 ⇒ 在场归零（这就是当初会静默变错的那条路）
    expect(splitVehiclesByBasePresence([vehicle], CANVAS_ANCHOR).atBase).toHaveLength(0)
    // 拿不到基地点时不猜：一律记在路上，而不是"看起来正常"地归堆
    expect(splitVehiclesByBasePresence([vehicle], null).atBase).toHaveLength(0)
    expect(splitVehiclesByBasePresence([vehicle], null).onRoad).toHaveLength(1)
    expect(basePositionFromStations([])).toBeNull()
  })

  test('L0 coverage circles have a single source', () => {
    expect(L0_COVERAGE_CIRCLES).toHaveLength(2)
    expect(L0_COVERAGE_CIRCLES[0].center).toEqual(ZJF_L0_COVERAGE.chuanjiang.center)
    expect(L0_COVERAGE_CIRCLES[1].center).toEqual(ZJF_L0_COVERAGE.dieshiqiao.center)
    for (const circle of L0_COVERAGE_CIRCLES) {
      expect(circle.radiusMeters).toBe(20_000)
    }
  })
})

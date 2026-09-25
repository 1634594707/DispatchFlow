import {
  buildOperationalStationMarkers,
  isInsideBase,
  stationGeoPosition,
} from './parkGeoMapLayers'
import type { GeoMapMarker, GeoMapPolyline } from './types'
import type { ParkStation } from '@/types/park'

export type DeliverySceneId = 'core-loop' | 'hub-express' | 'charge-recovery'

export interface DeliveryScenePlan {
  id: DeliverySceneId
  name: string
  summary: string
  serviceWindow: string
  targetMinutes: number
  routeCodes: string[]
  color: string
  kind: 'delivery' | 'charging'
}

/**
 * 运营场景是**业务配置**（哪条环线、目标几分钟、什么颜色），不是地理数据，
 * 所以留在这里；线要画在哪几个点上，一律按 `routeCodes` 去接口返回的站点里查（§6.4）。
 */
export const DELIVERY_SCENE_PLANS: DeliveryScenePlan[] = [
  {
    id: 'core-loop',
    name: '南排门市 → 代发仓',
    summary: '高频短驳主环线，优先使用 SOC ≥ 45% 的空闲车辆。',
    serviceWindow: '06:00–22:00',
    targetMinutes: 9,
    routeCodes: ['ZJF-IDLE-01', 'ZJF-PICK-01', 'ZJF-DROP-01'],
    color: '#22d3ee',
    kind: 'delivery',
  },
  {
    id: 'hub-express',
    name: '代发仓 → 快递接驳点',
    summary: '集中出库后的批量转运线，按波次合单并限制空驶返回。',
    serviceWindow: '08:00–22:00',
    targetMinutes: 7,
    routeCodes: ['ZJF-IDLE-01', 'ZJF-DROP-01', 'ZJF-EXPRESS-01'],
    color: '#fbbf24',
    kind: 'delivery',
  },
  {
    id: 'charge-recovery',
    name: '低电车辆 → 找家纺网基地',
    summary: 'SOC < 25% 返回基地充电；25%–45% 仅接顺路单并预留返航电量。',
    serviceWindow: '24 小时',
    targetMinutes: 6,
    routeCodes: ['ZJF-DROP-02', 'ZJF-CHG-01'],
    color: '#fb7185',
    kind: 'charging',
  },
]

/** 按站点编码查真实坐标；查不到返回 null，由调用方计入"缺位"而不是静默画错。 */
export function stationPositionByCode(
  stations: ParkStation[],
  code: string,
): [number, number] | null {
  const hit = stations.find((station) => station.stationCode === code)
  return hit ? stationGeoPosition(hit) : null
}

/** 场景里解析不到坐标的站点编码（接口少点/点没坐标时给界面提示用，不再静默截断）。 */
export function unresolvedPlanCodes(
  stations: ParkStation[],
  sceneId?: DeliverySceneId,
): string[] {
  return DELIVERY_SCENE_PLANS.filter((scene) => !sceneId || scene.id === sceneId).flatMap(
    (scene) => scene.routeCodes.filter((code) => stationPositionByCode(stations, code) === null),
  )
}

export function buildOperationsPlanPolylines(
  stations: ParkStation[],
  sceneId?: DeliverySceneId,
): GeoMapPolyline[] {
  return DELIVERY_SCENE_PLANS.filter((scene) => !sceneId || scene.id === sceneId).flatMap(
    (scene) => {
      const path = scene.routeCodes.flatMap((code) => {
        const position = stationPositionByCode(stations, code)
        return position ? [position] : []
      })
      if (path.length < 2) return []
      return [
        {
          id: `operations-${scene.id}`,
          path,
          strokeColor: scene.color,
          strokeWeight: scene.kind === 'charging' ? 5 : 6,
          strokeOpacity: 0.9,
          lineDash: scene.kind === 'charging' ? [10, 8] : undefined,
          zIndex: 80,
        },
      ]
    },
  )
}

/**
 * 服务点图层 + 一个"基地 POI"。
 *
 * <p>挂基地半径内的那些点（`ZJF-IDLE-01` 与 `ZJF-CHG-01` 在库里就是同一个坐标）不再各画一个图标，
 * 而是并进基地 POI 的 `· N 个点位` 计数里（§7.5「14 对象叠一点」的正解：数据层保留真实坐标，
 * 图层侧收成一个徽标）。
 */
export function buildOperationsStationMarkers(
  stations: ParkStation[],
  options: { basePosition: [number, number] | null; baseVehicleCount?: number },
): GeoMapMarker[] {
  const base = options.basePosition
  const atBase = base
    ? stations.filter((station) => {
        const position = stationGeoPosition(station)
        return position !== null && isInsideBase(position, base)
      })
    : []
  const atBaseIds = new Set(atBase.map((station) => station.stationId))
  const markers = buildOperationalStationMarkers(stations.filter((s) => !atBaseIds.has(s.stationId)))
  if (!base) return markers
  const label = [
    '找家纺网基地',
    `${options.baseVehicleCount ?? 0} 辆车在场`,
    atBase.length ? `${atBase.length} 个点位` : null,
  ]
    .filter(Boolean)
    .join(' · ')
  markers.push({
    id: 'operations-base',
    position: base,
    label,
    status: 'charging',
    markerType: 'charging',
    iconUrl: '/icons/map-station-charging.svg',
    showLabel: false,
    aggregatedCount: atBase.length || undefined,
    aggregatedLabels: atBase.map((s) => s.stationCode ?? String(s.stationId)),
  })
  return markers
}

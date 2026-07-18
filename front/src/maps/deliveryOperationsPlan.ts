import { ZJF_STATION_ANCHORS } from './zjfStationAnchors'
import type { GeoMapMarker, GeoMapPolyline } from './types'

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

export const DELIVERY_SCENE_PLANS: DeliveryScenePlan[] = [
  {
    id: 'core-loop',
    name: '南排门市 → 代发仓',
    summary: '高频短驳主环线，优先使用 SOC ≥ 45% 的空闲车辆。',
    serviceWindow: '06:00–22:00',
    targetMinutes: 9,
    routeCodes: ['ZJF-PICK-02', 'ZJF-PICK-01', 'ZJF-IDLE-01', 'ZJF-DROP-01'],
    color: '#22d3ee',
    kind: 'delivery',
  },
  {
    id: 'hub-express',
    name: '代发仓 → 快递接驳点',
    summary: '集中出库后的批量转运线，按波次合单并限制空驶返回。',
    serviceWindow: '08:00–22:00',
    targetMinutes: 7,
    routeCodes: ['ZJF-DROP-01', 'ZJF-DROP-03', 'ZJF-EXPRESS-01'],
    color: '#fbbf24',
    kind: 'delivery',
  },
  {
    id: 'charge-recovery',
    name: '低电车辆 → 就近充电站',
    summary: 'SOC < 25% 强制退出派单池；25%–45% 仅接顺路单并机会补能。',
    serviceWindow: '24 小时',
    targetMinutes: 6,
    routeCodes: ['ZJF-DROP-02', 'ZJF-CHG-05', 'ZJF-IDLE-01', 'ZJF-CHG-01'],
    color: '#fb7185',
    kind: 'charging',
  },
]

function anchorPosition(code: string): [number, number] | null {
  const anchor = ZJF_STATION_ANCHORS.find((item) => item.code === code)
  return anchor ? [anchor.lng, anchor.lat] : null
}

export function buildOperationsPlanPolylines(sceneId?: DeliverySceneId): GeoMapPolyline[] {
  return DELIVERY_SCENE_PLANS.filter((scene) => !sceneId || scene.id === sceneId).flatMap(
    (scene) => {
      const path = scene.routeCodes.flatMap((code) => {
        const position = anchorPosition(code)
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

export function buildOperationsStationMarkers(): GeoMapMarker[] {
  return ZJF_STATION_ANCHORS.map((station) => ({
    id: `operations-station-${station.code}`,
    position: [station.lng, station.lat],
    label: `${station.name} · ${station.code}`,
    status: station.role === 'charging' ? 'charging' : station.role === 'idle' ? 'idle' : 'station',
  }))
}

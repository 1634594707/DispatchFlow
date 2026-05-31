import { getMapConfig } from './config'
import type { GeoMapHandle, GeoMapInitOptions, GeoMapMarker, GeoMapPolygon, MapProvider } from './types'

declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string }
  }
}

type AMapMarker = { setMap: (map: unknown | null) => void }

export class AmapProvider implements MapProvider {
  readonly id = 'AMAP' as const

  isAvailable(): boolean {
    const { amapKey, amapSecurityCode } = getMapConfig()
    return !!amapKey && !!amapSecurityCode
  }

  async createMap(options: GeoMapInitOptions): Promise<GeoMapHandle> {
    const { amapKey, amapSecurityCode } = getMapConfig()
    if (!amapKey || !amapSecurityCode) {
      throw new Error('高德 Key 或安全密钥未配置，请复制 front/.env.example 为 .env.local')
    }

    window._AMapSecurityConfig = { securityJsCode: amapSecurityCode }
    const { default: AMapLoader } = await import('@amap/amap-jsapi-loader')
    const AMap = await AMapLoader.load({
      key: amapKey,
      version: '2.0',
      plugins: ['AMap.Scale'],
    })

    const map = new AMap.Map(options.container, {
      zoom: options.zoom,
      center: options.center,
      viewMode: '2D',
    })
    map.addControl(new AMap.Scale())

    let markers: AMapMarker[] = []
    let polygons: Array<{ setMap: (map: unknown | null) => void }> = []

    return {
      destroy() {
        markers.forEach((marker) => marker.setMap(null))
        markers = []
        polygons.forEach((polygon) => polygon.setMap(null))
        polygons = []
        map.destroy()
      },
      setCenter(center) {
        map.setCenter(center)
      },
      setZoom(zoom) {
        map.setZoom(zoom)
      },
      setMarkers(nextMarkers: GeoMapMarker[], options?: { fitView?: boolean }) {
        markers.forEach((marker) => marker.setMap(null))
        markers = nextMarkers.map((item) => {
          const marker = new AMap.Marker({
            position: item.position,
            title: item.label ?? item.id,
            label: item.label
              ? {
                  content: item.label,
                  direction: 'top',
                }
              : undefined,
          })
          marker.setMap(map)
          return marker as AMapMarker
        })
        if (options?.fitView && markers.length > 0) {
          map.setFitView(markers, false, [80, 80, 80, 80])
        }
      },
      setPolygons(nextPolygons: GeoMapPolygon[]) {
        polygons.forEach((polygon) => polygon.setMap(null))
        polygons = nextPolygons.map((item) => {
          const polygon = new AMap.Polygon({
            path: item.path,
            strokeColor: item.strokeColor ?? '#00d4aa',
            strokeWeight: 2,
            fillColor: item.fillColor ?? 'rgba(0, 212, 170, 0.12)',
            fillOpacity: 0.35,
          })
          polygon.setMap(map)
          return polygon
        })
      },
    }
  }
}

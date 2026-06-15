import { getMapConfig } from './config'
import { waitForAmapAuth } from './amapAuth'
import type {
  GeoMapCircle,
  GeoMapHandle,
  GeoMapInitOptions,
  GeoMapMarker,
  GeoMapPolygon,
  GeoMapPolyline,
  MapProvider,
} from './types'

declare global {
  interface Window {
    _AMapSecurityConfig?: { securityJsCode: string }
  }
}

type AMapOverlay = { setMap: (map: unknown | null) => void }

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

    await waitForAmapAuth(map, options.container)

    let markers: AMapOverlay[] = []
    let polygons: AMapOverlay[] = []
    let polylines: AMapOverlay[] = []
    let circles: AMapOverlay[] = []

    const clear = (overlays: AMapOverlay[]) => {
      overlays.forEach((overlay) => overlay.setMap(null))
      overlays.length = 0
    }

    return {
      destroy() {
        clear(markers)
        clear(polygons)
        clear(polylines)
        clear(circles)
        map.destroy()
      },
      setCenter(center) {
        map.setCenter(center)
      },
      setZoom(zoom) {
        map.setZoom(zoom)
      },
      setMarkers(nextMarkers: GeoMapMarker[], fitOptions?: { fitView?: boolean }) {
        clear(markers)
        markers = nextMarkers.map((item) => {
          const markerOptions: Record<string, unknown> = {
            position: item.position,
            title: item.label ?? item.id,
            angle: item.heading ?? 0,
            offset: new AMap.Pixel(-18, -18),
          }
          if (item.iconUrl) {
            markerOptions.icon = new AMap.Icon({
              image: item.iconUrl,
              size: new AMap.Size(36, 36),
              imageSize: new AMap.Size(36, 36),
            })
          }
          if (item.label) {
            markerOptions.label = {
              content: item.label,
              direction: 'top',
              offset: new AMap.Pixel(0, -8),
            }
          }
          const marker = new AMap.Marker(markerOptions)
          marker.setMap(map)
          return marker as AMapOverlay
        })
        if (fitOptions?.fitView && markers.length > 0) {
          map.setFitView(markers, false, [80, 80, 80, 80])
        }
      },
      setPolygons(nextPolygons: GeoMapPolygon[]) {
        clear(polygons)
        polygons = nextPolygons.map((item) => {
          const polygon = new AMap.Polygon({
            path: item.path,
            strokeColor: item.strokeColor ?? '#00d4aa',
            strokeWeight: item.strokeWeight ?? 2,
            fillColor: item.fillColor ?? 'rgba(0, 212, 170, 0.12)',
            fillOpacity: item.fillOpacity ?? 0.35,
            zIndex: item.zIndex ?? 10,
          })
          polygon.setMap(map)
          return polygon as AMapOverlay
        })
      },
      setPolylines(nextPolylines: GeoMapPolyline[]) {
        clear(polylines)
        polylines = nextPolylines.map((item) => {
          const polyline = new AMap.Polyline({
            path: item.path,
            strokeColor: item.strokeColor ?? '#3ea6ff',
            strokeWeight: item.strokeWeight ?? 4,
            strokeOpacity: item.strokeOpacity ?? 0.85,
            strokeStyle: item.lineDash?.length ? 'dashed' : 'solid',
            strokeDasharray: item.lineDash,
            zIndex: item.zIndex ?? 50,
            showDir: true,
          })
          polyline.setMap(map)
          return polyline as AMapOverlay
        })
      },
      setCircles(nextCircles: GeoMapCircle[]) {
        clear(circles)
        circles = nextCircles.map((item) => {
          const circle = new AMap.Circle({
            center: item.center,
            radius: item.radiusMeters,
            strokeColor: item.strokeColor ?? 'rgba(100, 149, 237, 0.5)',
            strokeWeight: item.strokeWeight ?? 1,
            fillColor: item.fillColor ?? 'rgba(100, 149, 237, 0.06)',
            fillOpacity: item.fillOpacity ?? 0.2,
            zIndex: item.zIndex ?? 2,
            bubble: true,
          })
          circle.setMap(map)
          return circle as AMapOverlay
        })
      },
      fitViewToPoints(points, padding = [72, 72, 72, 72]) {
        if (!points.length) return
        if (points.length === 1) {
          map.setCenter(points[0])
          map.setZoom(17)
          return
        }
        let minLng = points[0][0]
        let maxLng = points[0][0]
        let minLat = points[0][1]
        let maxLat = points[0][1]
        for (const [lng, lat] of points) {
          minLng = Math.min(minLng, lng)
          maxLng = Math.max(maxLng, lng)
          minLat = Math.min(minLat, lat)
          maxLat = Math.max(maxLat, lat)
        }
        const bounds = new AMap.Bounds([minLng, minLat], [maxLng, maxLat])
        map.setBounds(bounds, false, padding)
      },
    }
  }
}

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

    /**
     * 标记按 id 复用，不再每轮全删重建。
     *
     * 原来 `setMarkers` 每次 `clear` + 重建：1.5 s 一次的轮询于是变成"车跳一下、标签闪一下、
     * 正要点到的那一针被换掉"。复用之后车可以从旧位置补间到新位置（下面 VEHICLE_TWEEN_MS），
     * 点击命中的也不再是一个刚被换掉的实例。
     */
    interface MarkerEntry {
      overlay: any
      item: GeoMapMarker
      raf: number | null
    }
    const markerEntries = new Map<string, MarkerEntry>()
    let polygons: AMapOverlay[] = []
    let polylines: AMapOverlay[] = []
    let circles: AMapOverlay[] = []

    const clear = (overlays: AMapOverlay[]) => {
      overlays.forEach((overlay) => overlay.setMap(null))
      overlays.length = 0
    }

    /** 车在两次轮询之间走一小步：补间；跳太远（换单/瞬移）就直接落位，别演一段穿越动画。 */
    const VEHICLE_TWEEN_MS = 1200
    const TWEEN_MAX_DEG = 0.004

    const markerOptions = (item: GeoMapMarker) => {
      const markerSize = item.selected ? 44 : 36
      const options: Record<string, unknown> = {
        position: item.position,
        title: item.label ?? item.id,
        angle: item.heading ?? 0,
        offset: new AMap.Pixel(-markerSize / 2, -markerSize / 2),
        zIndex: item.selected ? 180 : item.markerType === 'vehicle' ? 160 : 140,
      }
      if (item.iconUrl) {
        options.icon = new AMap.Icon({
          image: item.iconUrl,
          size: new AMap.Size(markerSize, markerSize),
          imageSize: new AMap.Size(markerSize, markerSize),
        })
      }
      if (item.label && item.showLabel !== false) {
        options.label = {
          content: item.label,
          direction: item.labelDirection ?? 'top',
          offset: new AMap.Pixel(item.labelOffset?.[0] ?? 0, item.labelOffset?.[1] ?? -8),
        }
      }
      return options
    }

    const applyMarkerState = (entry: MarkerEntry, item: GeoMapMarker) => {
      const markerSize = item.selected ? 44 : 36
      entry.overlay.setAngle(item.heading ?? 0)
      entry.overlay.setzIndex(item.selected ? 180 : item.markerType === 'vehicle' ? 160 : 140)
      entry.overlay.setOffset(new AMap.Pixel(-markerSize / 2, -markerSize / 2))
      if (item.iconUrl) {
        entry.overlay.setIcon(new AMap.Icon({
          image: item.iconUrl,
          size: new AMap.Size(markerSize, markerSize),
          imageSize: new AMap.Size(markerSize, markerSize),
        }))
      }
      entry.overlay.setLabel(item.label && item.showLabel !== false
        ? {
            content: item.label,
            direction: item.labelDirection ?? 'top',
            offset: new AMap.Pixel(item.labelOffset?.[0] ?? 0, item.labelOffset?.[1] ?? -8),
          }
        : null)
    }

    const stopTween = (entry: MarkerEntry) => {
      if (entry.raf != null) {
        cancelAnimationFrame(entry.raf)
        entry.raf = null
      }
    }

    const tweenTo = (entry: MarkerEntry, from: [number, number], to: [number, number]) => {
      const startedAt = performance.now()
      const step = (now: number) => {
        const ratio = Math.min(1, (now - startedAt) / VEHICLE_TWEEN_MS)
        entry.overlay.setPosition([from[0] + (to[0] - from[0]) * ratio, from[1] + (to[1] - from[1]) * ratio])
        entry.raf = ratio < 1 ? requestAnimationFrame(step) : null
      }
      entry.raf = requestAnimationFrame(step)
    }

    return {
      destroy() {
        markerEntries.forEach((entry) => {
          stopTween(entry)
          entry.overlay.setMap(null)
        })
        markerEntries.clear()
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
        const alive = new Set<string>()
        for (const item of nextMarkers) {
          alive.add(item.id)
          const existing = markerEntries.get(item.id)
          if (!existing) {
            const marker = new AMap.Marker(markerOptions(item))
            // 点击时再查一次当前 item：实例被复用了，闭包里那份可能是上一轮的标签/选中态
            marker.on('click', () => {
              const current = markerEntries.get(item.id)
              options.onMarkerClick?.(current ? current.item : item)
            })
            marker.setMap(map)
            markerEntries.set(item.id, { overlay: marker, item, raf: null })
            continue
          }
          stopTween(existing)
          const from = existing.item.position
          const moved = from[0] !== item.position[0] || from[1] !== item.position[1]
          applyMarkerState(existing, item)
          if (moved && item.markerType === 'vehicle'
            && Math.abs(from[0] - item.position[0]) < TWEEN_MAX_DEG
            && Math.abs(from[1] - item.position[1]) < TWEEN_MAX_DEG) {
            tweenTo(existing, from, item.position)
          } else if (moved) {
            existing.overlay.setPosition(item.position)
          }
          existing.item = item
        }
        markerEntries.forEach((entry, id) => {
          if (alive.has(id)) return
          stopTween(entry)
          entry.overlay.setMap(null)
          markerEntries.delete(id)
        })
        if (fitOptions?.fitView && markerEntries.size > 0) {
          map.setFitView([...markerEntries.values()].map((entry) => entry.overlay), false, [80, 80, 80, 80])
        }
      },
      setPolygons(nextPolygons: GeoMapPolygon[]) {
        clear(polygons)
        polygons = nextPolygons.map((item) => {
          const polygon = new AMap.Polygon({
            path: item.path,
            strokeColor: item.strokeColor ?? '#2DE08A',
            strokeWeight: item.strokeWeight ?? 2,
            fillColor: item.fillColor ?? 'rgba(45, 224, 138, 0.12)',
            fillOpacity: item.fillOpacity ?? 0.35,
            strokeStyle: item.lineDash?.length ? 'dashed' : 'solid',
            strokeDasharray: item.lineDash,
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
            strokeColor: item.strokeColor ?? '#22C7E6',
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

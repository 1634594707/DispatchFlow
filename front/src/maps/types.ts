export type MapProviderId = 'AMAP' | 'SCHEMATIC'

export type GeoMapMarkerStatus = 'idle' | 'busy' | 'charging' | 'offline' | 'lowBattery'

export interface GeoMapMarker {
  id: string
  position: [number, number]
  label?: string
  labelDirection?: 'top' | 'right' | 'bottom' | 'left' | 'center'
  labelOffset?: [number, number]
  iconUrl?: string
  heading?: number
  status?: GeoMapMarkerStatus | string
  markerType?: 'vehicle' | 'pickup' | 'dropoff' | 'express' | 'charging' | 'idle' | 'target'
  selected?: boolean
  showLabel?: boolean
}

export interface GeoMapPolygon {
  id: string
  path: [number, number][]
  strokeColor?: string
  fillColor?: string
  strokeWeight?: number
  fillOpacity?: number
  lineDash?: number[]
  zIndex?: number
}

export interface GeoMapPolyline {
  id: string
  path: [number, number][]
  strokeColor?: string
  strokeWeight?: number
  strokeOpacity?: number
  lineDash?: number[]
  zIndex?: number
}

export interface GeoMapCircle {
  id: string
  center: [number, number]
  radiusMeters: number
  strokeColor?: string
  fillColor?: string
  strokeWeight?: number
  fillOpacity?: number
  zIndex?: number
}

export interface GeoMapInitOptions {
  container: HTMLElement
  /** [lng, lat] GCJ-02 */
  center: [number, number]
  zoom: number
  onMarkerClick?: (marker: GeoMapMarker) => void
}

export interface GeoMapHandle {
  destroy(): void
  setCenter(center: [number, number]): void
  setZoom(zoom: number): void
  setMarkers(markers: GeoMapMarker[], options?: { fitView?: boolean }): void
  setPolygons(polygons: GeoMapPolygon[]): void
  setPolylines(polylines: GeoMapPolyline[]): void
  setCircles(circles: GeoMapCircle[]): void
  /** Fit map viewport to GCJ-02 points (e.g. current route polyline). */
  fitViewToPoints(points: [number, number][], padding?: number[]): void
}

export interface MapProvider {
  readonly id: MapProviderId
  isAvailable(): boolean
  createMap(options: GeoMapInitOptions): Promise<GeoMapHandle>
}

export type MapProviderId = 'AMAP' | 'SCHEMATIC'

export interface GeoMapMarker {
  id: string
  position: [number, number]
  label?: string
}

export interface GeoMapPolygon {
  id: string
  path: [number, number][]
  strokeColor?: string
  fillColor?: string
}

export interface GeoMapInitOptions {
  container: HTMLElement
  /** [lng, lat] GCJ-02 */
  center: [number, number]
  zoom: number
}

export interface GeoMapHandle {
  destroy(): void
  setCenter(center: [number, number]): void
  setZoom(zoom: number): void
  setMarkers(markers: GeoMapMarker[], options?: { fitView?: boolean }): void
  setPolygons(polygons: GeoMapPolygon[]): void
}

export interface MapProvider {
  readonly id: MapProviderId
  isAvailable(): boolean
  createMap(options: GeoMapInitOptions): Promise<GeoMapHandle>
}

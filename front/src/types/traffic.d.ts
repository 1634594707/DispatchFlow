export interface TrafficSegment {
  segmentId: number
  fromNodeCode: string
  toNodeCode: string
  speedLimitKmh?: number
  congestionLevel: number
  status: string
  nearbyVehicleCount?: number
  affectedTaskCount?: number
}

export interface TrafficSummary {
  parkId: number
  maxCongestionLevel: number
  highCongestionSegmentCount: number
  pausedZoneCount: number
  disabledSegmentCount: number
}

export interface TrafficPauseZone {
  minX: number
  minY: number
  maxX: number
  maxY: number
  label?: string
}

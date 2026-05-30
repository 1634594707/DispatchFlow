export interface TrafficSegment {
  segmentId: number
  fromNodeCode: string
  toNodeCode: string
  speedLimitKmh?: number
  congestionLevel: number
  status: string
}

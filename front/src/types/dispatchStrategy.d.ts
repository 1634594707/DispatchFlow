export interface DispatchStrategyProfile {
  id: number
  profileName: string
  profileType: 'PRODUCTION' | 'EXPERIMENT'
  active: boolean
  grayPercent?: number
  parkId?: number | null
  weightDistance: number
  weightSocMargin: number
  weightPluggedStandbyBonus: number
  minAssignableSoc: number
  fullSoc: number
  remark?: string
  updatedAt?: string
}

export interface DispatchStrategyUpsertPayload {
  profileName: string
  profileType: string
  grayPercent?: number
  parkId?: number | null
  weightDistance: number
  weightSocMargin: number
  weightPluggedStandbyBonus: number
  minAssignableSoc: number
  fullSoc: number
  remark?: string
}

export interface StrategyChangeLog {
  id: number
  profileId: number
  profileName: string
  changeType: string
  operatorName?: string
  changeSummary?: string
  createdAt: string
}

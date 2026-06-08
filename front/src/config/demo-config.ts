/**
 * 一键演示模式配置
 * V5-D4/D5: Demo scripts & interval configuration
 */

export interface DemoOrderTemplate {
  pickupStationId: number
  dropoffStationId: number
  priority: 'NORMAL' | 'URGENT'
  remark?: string
}

export interface DemoConfig {
  /** 自动生成演示订单的间隔（毫秒），默认 5 分钟 */
  autoIntervalMs: number
  /** 演示订单模板列表 */
  orderTemplates: DemoOrderTemplate[]
}

export const DEMO_CONFIG: DemoConfig = {
  autoIntervalMs: 5 * 60 * 1000,
  orderTemplates: [
    { pickupStationId: 1, dropoffStationId: 2, priority: 'NORMAL' },
    { pickupStationId: 3, dropoffStationId: 4, priority: 'NORMAL' },
    { pickupStationId: 5, dropoffStationId: 6, priority: 'URGENT' },
    { pickupStationId: 2, dropoffStationId: 1, priority: 'NORMAL' },
    { pickupStationId: 4, dropoffStationId: 3, priority: 'NORMAL' },
    { pickupStationId: 6, dropoffStationId: 5, priority: 'URGENT' },
  ],
}
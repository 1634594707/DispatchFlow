/**
 * 一键演示模式配置
 * V5-D4/D5: Demo scripts & interval configuration
 */

export interface DemoConfig {
  /** 自动生成演示订单的间隔（毫秒），默认 5 分钟 */
  autoIntervalMs: number
}

export const DEMO_CONFIG: DemoConfig = {
  autoIntervalMs: 5 * 60 * 1000,
}

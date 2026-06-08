import { computed } from 'vue'
import { useWorkbenchStore } from '@/stores/workbench'
import type { DispatchPredictionAlert } from '@/types/alert'

const RATIO_WARNING_THRESHOLD = 1.5
const RATIO_CRITICAL_THRESHOLD = 0.8

/**
 * V5-N4: 派车成功率预测告警
 */
export function useDispatchPrediction() {
  const workbenchStore = useWorkbenchStore()

  /** 可用车辆 / 待派任务 比率 */
  const dispatchRatio = computed(() => {
    const available = workbenchStore.assignableVehicleCount
    const pending = workbenchStore.pendingCount
    if (pending === 0) return Infinity
    return available / pending
  })

  /** 风险等级 */
  const dispatchRiskLevel = computed<'safe' | 'warning' | 'critical'>(() => {
    const ratio = dispatchRatio.value
    if (ratio === Infinity) return 'safe'
    if (ratio < RATIO_CRITICAL_THRESHOLD) return 'critical'
    if (ratio < RATIO_WARNING_THRESHOLD) return 'warning'
    return 'safe'
  })

  /** 预估等待时间（基于比率估算） */
  const predictedWaitTime = computed(() => {
    const ratio = dispatchRatio.value
    if (ratio === Infinity) return 0
    if (ratio >= RATIO_WARNING_THRESHOLD) return 0
    // Rough estimate: each vehicle takes ~10 min per task
    const deficit = Math.max(0, 1 / ratio - 1)
    return Math.round(deficit * 10)
  })

  /** 生成预测告警 */
  const dispatchPredictionAlert = computed<DispatchPredictionAlert | null>(() => {
    const riskLevel = dispatchRiskLevel.value
    if (riskLevel === 'safe') return null
    const ratio = dispatchRatio.value === Infinity ? 999 : dispatchRatio.value
    const waitTime = predictedWaitTime.value
    let message = ''
    if (riskLevel === 'critical') {
      message = `派车资源严重不足！可用车辆/待派任务比率仅 ${ratio.toFixed(1)}，预估等待约 ${waitTime} 分钟`
    } else {
      message = `派车资源趋紧，比率 ${ratio.toFixed(1)}，建议关注任务积压`
    }
    return { ratio, riskLevel, estimatedWaitMinutes: waitTime, message }
  })

  return {
    dispatchRatio,
    dispatchRiskLevel,
    predictedWaitTime,
    dispatchPredictionAlert,
  }
}
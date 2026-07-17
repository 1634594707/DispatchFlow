import { onMounted, ref } from 'vue'
import { getParkMetadata, type ParkMetadata } from '@/api/park'
import { ZJF_PILOT_GEO } from '@/maps'

/**
 * 阶段七 7.3：园区元数据 composable。
 * 优先从后端 /api/admin/park/metadata 拉取园区元数据（数据库可配置多园区）；
 * 接口不可用时回退到硬编码 ZJF_PILOT_GEO，保证旧部署不中断。
 */
export function useParkMetadata(parkId?: number) {
  const metadata = ref<ParkMetadata | null>(null)
  const loading = ref(false)
  const error = ref<string>('')

  async function refresh() {
    loading.value = true
    error.value = ''
    try {
      const response = await getParkMetadata(parkId)
      if (response?.success && response.data) {
        metadata.value = response.data
      } else {
        error.value = response?.message || 'Park metadata unavailable'
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : 'Failed to load park metadata'
    } finally {
      loading.value = false
    }
  }

  onMounted(() => {
    void refresh()
  })

  /** 锚点 [lng, lat] — 优先后端元数据，回退 ZJF_PILOT_GEO */
  function anchor(): [number, number] {
    const m = metadata.value
    if (m?.anchorLng != null && m?.anchorLat != null) {
      return [Number(m.anchorLng), Number(m.anchorLat)]
    }
    return [ZJF_PILOT_GEO.anchorLng, ZJF_PILOT_GEO.anchorLat]
  }

  /** 园区尺寸（米）— 优先后端元数据，回退 ZJF_PILOT_GEO */
  function parkSizeMeters(): { width: number; height: number } {
    const m = metadata.value
    if (m?.parkWidthMeters != null && m?.parkHeightMeters != null) {
      return { width: Number(m.parkWidthMeters), height: Number(m.parkHeightMeters) }
    }
    return { width: ZJF_PILOT_GEO.parkWidthMeters, height: ZJF_PILOT_GEO.parkHeightMeters }
  }

  return {
    metadata,
    loading,
    error,
    refresh,
    anchor,
    parkSizeMeters,
  }
}

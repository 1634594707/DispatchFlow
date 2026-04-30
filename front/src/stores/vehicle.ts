import { defineStore } from 'pinia'
import { ref } from 'vue'
import { queryVehicles, getVehicleDetail } from '@/api/vehicle'
import type { VehicleAdminListItem, VehicleDetailResponse, VehicleQueryRequest } from '@/types/vehicle'
import type { PageResponse } from '@/types/api'

export const useVehicleStore = defineStore('vehicle', () => {
  const list = ref<VehicleAdminListItem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const detail = ref<VehicleDetailResponse | null>(null)
  const detailLoading = ref(false)

  async function fetchList(params: VehicleQueryRequest) {
    loading.value = true
    try {
      const res = await queryVehicles(params)
      const page = res.data as unknown as PageResponse<VehicleAdminListItem>
      list.value = page.records
      total.value = page.total
    } catch (e) {
      console.error('Failed to fetch vehicles', e)
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(vehicleId: number) {
    detailLoading.value = true
    try {
      const res = await getVehicleDetail(vehicleId)
      detail.value = res.data
    } catch (e) {
      console.error('Failed to fetch vehicle detail', e)
    } finally {
      detailLoading.value = false
    }
  }

  return { list, total, loading, detail, detailLoading, fetchList, fetchDetail }
})

import { defineStore } from 'pinia'
import { ref } from 'vue'
import { queryOrders, getOrderDetail } from '@/api/order'
import type { OrderAdminListItem, OrderDetailResponse, OrderQueryRequest } from '@/types/order'
import type { PageResponse } from '@/types/api'

export const useOrderStore = defineStore('order', () => {
  const list = ref<OrderAdminListItem[]>([])
  const total = ref(0)
  const loading = ref(false)
  const detail = ref<OrderDetailResponse | null>(null)
  const detailLoading = ref(false)

  async function fetchList(params: OrderQueryRequest) {
    loading.value = true
    try {
      const res = await queryOrders(params)
      const page = res.data as unknown as PageResponse<OrderAdminListItem>
      list.value = page.records
      total.value = page.total
    } catch (e) {
      console.error('Failed to fetch orders', e)
    } finally {
      loading.value = false
    }
  }

  async function fetchDetail(orderId: number) {
    detailLoading.value = true
    try {
      const res = await getOrderDetail(orderId)
      detail.value = res.data
    } catch (e) {
      console.error('Failed to fetch order detail', e)
    } finally {
      detailLoading.value = false
    }
  }

  return { list, total, loading, detail, detailLoading, fetchList, fetchDetail }
})

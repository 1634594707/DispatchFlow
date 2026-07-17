/**
 * V5-Q3: Mobile order form state & submission composable
 *
 * Extracted from ParkOrder.vue order creation logic.
 * Manages form state, park/station loading, and order submission.
 */
import { ref, reactive, computed } from 'vue'
import { message } from 'ant-design-vue'
import { createParkOrder, getParkStations, listParks } from '@/api/park'
import { loadMobileOrderMode, persistMobileOrderMode } from '@/constants/parkDelivery'
import type { MobileOrderMode } from '@/constants/parkDelivery'
import type { ParkOrderCreateRequest, ParkOrderCreateResponse, ParkSummary, ParkStation } from '@/types/park'

export function useMobileOrderForm() {
  const loadingParks = ref(false)
  const loadingStations = ref(false)
  const submitting = ref(false)
  const parks = ref<ParkSummary[]>([])
  const stations = ref<ParkStation[]>([])
  const lastCreatedOrder = ref<ParkOrderCreateResponse | null>(null)
  const mobileApiKey = ref('')

  const orderMode = ref<MobileOrderMode>(loadMobileOrderMode())

  const form = reactive<ParkOrderCreateRequest>({
    parkId: undefined,
    externalOrderNo: '',
    pickupStationId: undefined as unknown as number,
    dropoffStationId: undefined as unknown as number,
    routeId: undefined as number | undefined,
    priority: 'P1',
    orderPriority: 'NORMAL',
    weight: undefined as number | undefined,
    remark: '',
  })

  const parkOptions = computed(() =>
    parks.value.map(park => ({
      value: park.parkId,
      label: park.parkName,
    })),
  )

  const isSinglePark = computed(() => parks.value.length <= 1)

  const lockedParkName = computed(() => {
    const park = parks.value.find(item => item.parkId === form.parkId)
    return park?.parkName || '叠石桥 L1 试点'
  })

  function resolveDefaultMobileApiKey(): string {
    return (
      sessionStorage.getItem('fsd_mobile_api_key')?.trim() ||
      (import.meta.env.VITE_MOBILE_API_KEY as string | undefined)?.trim() ||
      ''
    )
  }

  function persistMobileApiKey() {
    const trimmed = mobileApiKey.value.trim()
    if (trimmed) sessionStorage.setItem('fsd_mobile_api_key', trimmed)
    else sessionStorage.removeItem('fsd_mobile_api_key')
  }

  function handleOrderModeUpdate(mode: MobileOrderMode) {
    if (orderMode.value === mode) return
    orderMode.value = mode
    persistMobileOrderMode(mode)
    form.pickupStationId = undefined as unknown as number
    form.dropoffStationId = undefined as unknown as number
    form.routeId = undefined
  }

  function validateForm(): boolean {
    if (!form.pickupStationId) {
      message.error('请选择取货站点')
      return false
    }
    if (!form.dropoffStationId) {
      message.error('请选择送货站点')
      return false
    }
    if (form.pickupStationId === form.dropoffStationId) {
      message.error('取货站点和送货站点不能相同')
      return false
    }
    return true
  }

  async function submitOrder(apiKey?: string): Promise<ParkOrderCreateResponse | null> {
    if (!validateForm()) return null

    submitting.value = true
    try {
      const response = await createParkOrder(
        {
          parkId: form.parkId,
          externalOrderNo: form.externalOrderNo?.trim() || undefined,
          pickupStationId: form.pickupStationId,
          dropoffStationId: form.dropoffStationId,
          routeId: form.routeId,
          priority: form.priority || 'P1',
          orderPriority: form.orderPriority || 'NORMAL',
          weight: form.weight,
          remark: form.remark?.trim() || undefined,
        },
        apiKey,
      )
      lastCreatedOrder.value = response.data
      form.externalOrderNo = ''
      form.remark = ''
      message.success('订单已创建')
      return response.data
    } catch (err: unknown) {
      const msg =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        (err instanceof Error ? err.message : '下单失败')
      message.error(msg.includes('X-Mobile-Api-Key') ? `${msg}（请配置 VITE_MOBILE_API_KEY）` : msg)
      return null
    } finally {
      submitting.value = false
    }
  }

  async function fetchParks() {
    loadingParks.value = true
    try {
      const response = await listParks()
      parks.value = response.data || []
      if (!form.parkId) {
        const defaultPark = parks.value.find(park => park.defaultPark) || parks.value[0]
        if (defaultPark) form.parkId = defaultPark.parkId
      }
    } finally {
      loadingParks.value = false
    }
  }

  async function fetchStations() {
    if (!form.parkId) {
      stations.value = []
      return
    }
    loadingStations.value = true
    try {
      const response = await getParkStations(form.parkId)
      stations.value = response.data || []
    } finally {
      loadingStations.value = false
    }
  }

  return {
    loadingParks,
    loadingStations,
    submitting,
    parks,
    stations,
    lastCreatedOrder,
    mobileApiKey,
    orderMode,
    form,
    parkOptions,
    isSinglePark,
    lockedParkName,
    resolveDefaultMobileApiKey,
    persistMobileApiKey,
    handleOrderModeUpdate,
    validateForm,
    submitOrder,
    fetchParks,
    fetchStations,
  }
}
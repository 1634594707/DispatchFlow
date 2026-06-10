import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { globalSearch } from '@/api/search'
import { useAuthStore } from '@/stores/auth'
import { useWorkbenchStore } from '@/stores/workbench'
import { buildNavCommandItems } from '@/config/navigation'
import type { GlobalSearchItem } from '@/types/phase10'

export interface CommandPaletteItem {
  key: string
  label: string
  hint?: string
  group: string
  action: () => void | Promise<void>
}

export function useCommandPalette() {
  const router = useRouter()
  const authStore = useAuthStore()
  const workbenchStore = useWorkbenchStore()
  const visible = ref(false)
  const keyword = ref('')
  const loading = ref(false)
  const searchResults = ref<GlobalSearchItem[]>([])
  const activeIndex = ref(0)

  function open() {
    visible.value = true
    keyword.value = ''
    searchResults.value = []
    activeIndex.value = 0
  }

  function close() {
    visible.value = false
  }

  function toggle() {
    if (visible.value) {
      close()
    } else {
      open()
    }
  }

  async function runSearch() {
    const q = keyword.value.trim()
    if (q.length < 2) {
      searchResults.value = []
      return
    }
    loading.value = true
    try {
      const res = await globalSearch(q, 12)
      searchResults.value = res.data.items || []
      activeIndex.value = 0
    } catch {
      searchResults.value = []
    } finally {
      loading.value = false
    }
  }

  function buildOperationCommands(): CommandPaletteItem[] {
    if (!authStore.canWrite) {
      return []
    }
    const ops: CommandPaletteItem[] = [
      {
        key: 'op-refresh-workbench',
        label: '刷新工作台',
        group: '快捷操作',
        hint: '重新加载任务池',
        action: async () => {
          await workbenchStore.fetchQueue()
          void router.push('/workbench')
        },
      },
      {
        key: 'op-open-tracking',
        label: '打开车辆监控大屏',
        group: '快捷操作',
        action: () => { void router.push('/vehicle-tracking') },
      },
      {
        key: 'op-open-exceptions',
        label: '打开异常列表',
        group: '快捷操作',
        action: () => { void router.push('/exceptions?status=OPEN') },
      },
      {
        key: 'op-open-field-ops',
        label: '创建现场工单',
        group: '快捷操作',
        action: () => { void router.push('/field-ops/work-orders') },
      },
      {
        key: 'op-pause-dispatch',
        label: '前往调度策略',
        group: '快捷操作',
        hint: '暂停区域 / 派单策略',
        action: () => { void router.push('/system/dispatch-strategy') },
      },
    ]
    if (workbenchStore.selectedTaskId) {
      const taskId = workbenchStore.selectedTaskId
      ops.unshift({
        key: 'op-goto-selected-task',
        label: `跳转选中任务 #${taskId}`,
        group: '快捷操作',
        action: () => { void router.push(`/tasks/${taskId}`) },
      })
      ops.unshift({
        key: 'op-open-workbench',
        label: '打开工作台并定位任务',
        group: '快捷操作',
        action: () => { void router.push({ path: '/workbench', query: { taskId: String(taskId) } }) },
      })
    }
    return ops
  }

  function buildItems(): CommandPaletteItem[] {
    const navItems: CommandPaletteItem[] = buildNavCommandItems(authStore.user?.role).map((cmd) => ({
      key: cmd.key,
      label: cmd.label,
      hint: cmd.hint,
      group: cmd.group,
      action: () => { void router.push(cmd.path) },
    }))
    const q = keyword.value.trim().toLowerCase()
    const filteredNav = q
      ? navItems.filter((item) => item.label.toLowerCase().includes(q) || item.hint?.includes(q))
      : navItems
    const opItems = buildOperationCommands().filter((item) =>
      !q || item.label.toLowerCase().includes(q) || item.hint?.toLowerCase().includes(q),
    )
    const searchItems: CommandPaletteItem[] = searchResults.value.map((hit) => ({
      key: `search-${hit.type}-${hit.id}`,
      label: hit.title,
      hint: hit.subtitle || hit.code,
      group: '搜索',
      action: () => { void router.push(hit.routePath) },
    }))
    return [...filteredNav, ...opItems, ...searchItems]
  }

  function onKeydown(event: KeyboardEvent) {
    const isMetaK = (event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k'
    if (isMetaK) {
      event.preventDefault()
      toggle()
      return
    }
    if (!visible.value) {
      return
    }
    const items = buildItems()
    if (event.key === 'Escape') {
      event.preventDefault()
      close()
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      activeIndex.value = items.length === 0 ? 0 : (activeIndex.value + 1) % items.length
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      activeIndex.value = items.length === 0 ? 0 : (activeIndex.value - 1 + items.length) % items.length
      return
    }
    if (event.key === 'Enter' && items.length > 0) {
      event.preventDefault()
      const item = items[activeIndex.value]
      if (item) {
        void item.action()
        close()
      }
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))
  onUnmounted(() => window.removeEventListener('keydown', onKeydown))

  return {
    visible,
    keyword,
    loading,
    activeIndex,
    open,
    close,
    toggle,
    runSearch,
    buildItems,
  }
}

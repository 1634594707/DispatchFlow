import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { globalSearch } from '@/api/search'
import type { GlobalSearchItem } from '@/types/phase10'

export interface CommandPaletteItem {
  key: string
  label: string
  hint?: string
  group: '导航' | '搜索' | '操作'
  action: () => void | Promise<void>
}

const NAV_COMMANDS: Omit<CommandPaletteItem, 'action'>[] = [
  { key: 'nav-workbench', label: '调度工作台', group: '导航', hint: '/workbench' },
  { key: 'nav-dashboard', label: '调度看板', group: '导航', hint: '/dashboard' },
  { key: 'nav-tasks', label: '调度任务', group: '导航', hint: '/tasks' },
  { key: 'nav-vehicles', label: '车辆管理', group: '导航', hint: '/vehicles' },
  { key: 'nav-exceptions', label: '异常任务', group: '导航', hint: '/exceptions' },
  { key: 'nav-digital-twin', label: '数字孪生', group: '导航', hint: '/digital-twin' },
  { key: 'nav-system-health', label: '系统健康', group: '导航', hint: '/system/health' },
]

const NAV_PATHS: Record<string, string> = {
  'nav-workbench': '/workbench',
  'nav-dashboard': '/dashboard',
  'nav-tasks': '/tasks',
  'nav-vehicles': '/vehicles',
  'nav-exceptions': '/exceptions',
  'nav-digital-twin': '/digital-twin',
  'nav-system-health': '/system/health',
}

export function useCommandPalette() {
  const router = useRouter()
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

  function buildItems(): CommandPaletteItem[] {
    const items: CommandPaletteItem[] = NAV_COMMANDS.map((cmd) => ({
      ...cmd,
      action: () => { void router.push(NAV_PATHS[cmd.key]) },
    }))
    const q = keyword.value.trim().toLowerCase()
    const filteredNav = q
      ? items.filter((item) => item.label.toLowerCase().includes(q) || item.hint?.includes(q))
      : items
    const searchItems: CommandPaletteItem[] = searchResults.value.map((hit) => ({
      key: `search-${hit.type}-${hit.id}`,
      label: hit.title,
      hint: hit.subtitle || hit.code,
      group: '搜索',
      action: () => { void router.push(hit.routePath) },
    }))
    return [...filteredNav, ...searchItems]
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

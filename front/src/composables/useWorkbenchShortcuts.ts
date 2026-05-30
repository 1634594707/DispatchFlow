import { onMounted, onUnmounted, type Ref } from 'vue'

export interface WorkbenchShortcutHandlers {
  refresh: () => void | Promise<void>
  autoAssignSelected: () => void | Promise<void>
  openManualAssign: () => void
  moveSelection: (delta: number) => void
}

export function useWorkbenchShortcuts(enabled: Ref<boolean>, handlers: WorkbenchShortcutHandlers) {
  function isTypingTarget(target: EventTarget | null) {
    if (!(target instanceof HTMLElement)) return false
    const tag = target.tagName
    return tag === 'INPUT' || tag === 'TEXTAREA' || target.isContentEditable
  }

  function onKeydown(event: KeyboardEvent) {
    if (!enabled.value || isTypingTarget(event.target)) return
    const key = event.key.toLowerCase()
    if (key === 'r') {
      event.preventDefault()
      void handlers.refresh()
      return
    }
    if (key === 'a') {
      event.preventDefault()
      void handlers.autoAssignSelected()
      return
    }
    if (key === 'm') {
      event.preventDefault()
      handlers.openManualAssign()
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      handlers.moveSelection(1)
      return
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault()
      handlers.moveSelection(-1)
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))
  onUnmounted(() => window.removeEventListener('keydown', onKeydown))
}

import { ref, onMounted, onUnmounted } from 'vue'
import type { Ref } from 'vue'

export interface PWAInstallState {
  /** Whether the app can be installed to home screen */
  canInstall: Ref<boolean>
  /** Whether a new version is available */
  hasUpdate: Ref<boolean>
  /** Lifecycle disposition: 'idle' | 'dismissed' | 'later' | 'installed' | 'never' */
  disposition: Ref<PwaDisposition>
  /** Trigger the install prompt */
  install: () => Promise<boolean>
  /** Activate the new version */
  activateUpdate: () => void
  /** Dismiss the update notification */
  dismissUpdate: () => void
  /** Dismiss the install prompt (user declined for now) */
  dismissInstall: () => void
  /** Permanently decline install (do not show again) */
  neverRemindInstall: () => void
}

export type PwaDisposition = 'idle' | 'dismissed' | 'later' | 'installed' | 'never'

const STORAGE_KEY = 'fsd.pwa.install.state.v1'
/** Cooldown for the "later" disposition: 7 days (in ms). */
const LATER_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000
/** Cooldown for the soft "dismissed" disposition: 24 hours (in ms). */
const DISMISSED_COOLDOWN_MS = 24 * 60 * 60 * 1000

interface PersistedState {
  disposition: PwaDisposition
  /** Epoch millis when the user last interacted with the prompt. */
  updatedAt: number
  /** Whether the app was successfully installed (separately recorded). */
  installed: boolean
}

let deferredPrompt: Event | null = null
/** Whether the browser still allows installation (reset on appinstalled / dismiss). */
let promptAvailable = false

/**
 * Composable for managing PWA installation and update lifecycle.
 *
 * Persistence model (P0-1):
 *   - 'installed': app was installed; prompt never shows again.
 *   - 'never':      user clicked "不再提醒"; prompt never shows again until reset.
 *   - 'later':      user clicked "稍后提醒"; hidden until LATER_COOLDOWN_MS elapses.
 *   - 'dismissed':  user clicked "暂不"; hidden until DISMISSED_COOLDOWN_MS elapses.
 *   - 'idle':       no recorded disposition.
 *
 * `beforeinstallprompt` re-firing does NOT override a persisted disposition —
 * it only stores the event for later use; the prompt only reappears after the
 * cooldown window has elapsed.
 */
export function usePWAInstall(): PWAInstallState {
  const canInstall = ref(false)
  const hasUpdate = ref(false)
  const disposition = ref<PwaDisposition>('idle')

  let swRegistration: ServiceWorkerRegistration | null = null
  let updateInterval: ReturnType<typeof setInterval> | null = null

  function loadState(): PersistedState | null {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY)
      if (!raw) return null
      const parsed = JSON.parse(raw) as PersistedState
      if (!parsed.disposition) return null
      return parsed
    } catch {
      return null
    }
  }

  function saveState(state: PersistedState) {
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {
      // localStorage may be unavailable (private mode / disabled) — fall back to in-memory only
    }
  }

  /** Whether the persisted disposition allows the prompt to be shown again now. */
  function isPromptAllowedByPersistedState(): boolean {
    const state = loadState()
    if (!state) return true
    if (state.installed) return false
    if (state.disposition === 'never') return false
    if (state.disposition === 'installed') return false
    if (state.disposition === 'later') {
      return Date.now() - state.updatedAt >= LATER_COOLDOWN_MS
    }
    if (state.disposition === 'dismissed') {
      return Date.now() - state.updatedAt >= DISMISSED_COOLDOWN_MS
    }
    return true
  }

  function refreshCanInstall() {
    if (!promptAvailable) {
      canInstall.value = false
      return
    }
    canInstall.value = isPromptAllowedByPersistedState()
  }

  function handleBeforeInstall(event: Event) {
    event.preventDefault()
    deferredPrompt = event
    promptAvailable = true
    // Do NOT blindly flip canInstall to true here — respect persisted cooldown.
    refreshCanInstall()
  }

  async function install(): Promise<boolean> {
    if (!deferredPrompt) return false
    deferredPrompt.preventDefault?.()
    try {
      const promptEvent = deferredPrompt as unknown as { prompt: () => Promise<void>; userChoice: Promise<{ outcome: string }> }
      await promptEvent.prompt()
      const result = await promptEvent.userChoice
      canInstall.value = false
      deferredPrompt = null
      promptAvailable = false
      if (result.outcome === 'accepted') {
        disposition.value = 'installed'
        saveState({ disposition: 'installed', updatedAt: Date.now(), installed: true })
      } else {
        // User explicitly rejected the native prompt — treat as 'dismissed' cooldown
        disposition.value = 'dismissed'
        saveState({ disposition: 'dismissed', updatedAt: Date.now(), installed: false })
      }
      return result.outcome === 'accepted'
    } catch {
      return false
    }
  }

  function activateUpdate() {
    if (swRegistration?.waiting) {
      swRegistration.waiting.postMessage({ type: 'SKIP_WAITING' })
      window.location.reload()
    }
  }

  function dismissUpdate() {
    hasUpdate.value = false
  }

  /** "暂不" — soft dismissal, re-shows after DISMISSED_COOLDOWN_MS. */
  function dismissInstall() {
    canInstall.value = false
    disposition.value = 'dismissed'
    saveState({ disposition: 'dismissed', updatedAt: Date.now(), installed: false })
  }

  /** "不再提醒" — permanent opt-out until the user manually resets. */
  function neverRemindInstall() {
    canInstall.value = false
    disposition.value = 'never'
    saveState({ disposition: 'never', updatedAt: Date.now(), installed: false })
  }

  function onAppInstalled() {
    canInstall.value = false
    deferredPrompt = null
    promptAvailable = false
    disposition.value = 'installed'
    saveState({ disposition: 'installed', updatedAt: Date.now(), installed: true })
  }

  function onSWUpdate(registration: ServiceWorkerRegistration) {
    swRegistration = registration
    if (registration.waiting) {
      hasUpdate.value = true
    }

    registration.addEventListener('updatefound', () => {
      const newWorker = registration.installing
      if (newWorker) {
        newWorker.addEventListener('statechange', () => {
          if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
            hasUpdate.value = true
          }
        })
      }
    })
  }

  onMounted(async () => {
    // Restore persisted disposition on app boot (P0-1 requirement: page refresh / route switch
    // must continue to honor the cooldown).
    const persisted = loadState()
    if (persisted) {
      disposition.value = persisted.disposition
    }

    window.addEventListener('beforeinstallprompt', handleBeforeInstall)
    window.addEventListener('appinstalled', onAppInstalled)

    if ('serviceWorker' in navigator) {
      try {
        const registration = await navigator.serviceWorker.register('/sw.js')
        onSWUpdate(registration)

        // Periodically check for updates
        updateInterval = setInterval(() => {
          registration.update()
        }, 60 * 60 * 1000) // every hour
      } catch {
        // SW registration failed — non-critical
      }
    }

    // Initial visibility check — prompt may already be allowed or suppressed
    refreshCanInstall()
  })

  onUnmounted(() => {
    window.removeEventListener('beforeinstallprompt', handleBeforeInstall)
    window.removeEventListener('appinstalled', onAppInstalled)
    if (updateInterval) {
      clearInterval(updateInterval)
    }
  })

  return {
    canInstall,
    hasUpdate,
    disposition,
    install,
    activateUpdate,
    dismissUpdate,
    dismissInstall,
    neverRemindInstall,
  }
}

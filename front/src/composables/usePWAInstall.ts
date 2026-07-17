import { ref, onMounted, onUnmounted } from 'vue'
import type { Ref } from 'vue'

export interface PWAInstallState {
  /** Whether the app can be installed to home screen */
  canInstall: Ref<boolean>
  /** Whether a new version is available */
  hasUpdate: Ref<boolean>
  /** Trigger the install prompt */
  install: () => Promise<boolean>
  /** Activate the new version */
  activateUpdate: () => void
  /** Dismiss the update notification */
  dismissUpdate: () => void
  /** Dismiss the install prompt (user declined) */
  dismissInstall: () => void
}

let deferredPrompt: Event | null = null

/**
 * Composable for managing PWA installation and update lifecycle.
 * Should be called once at app root.
 */
export function usePWAInstall(): PWAInstallState {
  const canInstall = ref(false)
  const hasUpdate = ref(false)

  let swRegistration: ServiceWorkerRegistration | null = null
  let updateInterval: ReturnType<typeof setInterval> | null = null

  function handleBeforeInstall(event: Event) {
    event.preventDefault()
    deferredPrompt = event
    canInstall.value = true
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

  function dismissInstall() {
    canInstall.value = false
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
    window.addEventListener('beforeinstallprompt', handleBeforeInstall)
    window.addEventListener('appinstalled', () => {
      canInstall.value = false
      deferredPrompt = null
    })

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
  })

  onUnmounted(() => {
    window.removeEventListener('beforeinstallprompt', handleBeforeInstall)
    if (updateInterval) {
      clearInterval(updateInterval)
    }
  })

  return {
    canInstall,
    hasUpdate,
    install,
    activateUpdate,
    dismissUpdate,
    dismissInstall,
  }
}
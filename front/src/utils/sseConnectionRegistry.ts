type SSEStopFn = () => void

const activeStops = new Set<SSEStopFn>()
let initialized = false

function stopAll(): void {
  for (const stop of activeStops) {
    stop()
  }
  activeStops.clear()
}

function ensureInitialized(): void {
  if (initialized || typeof window === 'undefined') {
    return
  }
  initialized = true
  window.addEventListener('beforeunload', stopAll)
  window.addEventListener('pagehide', stopAll)
  if (import.meta.hot) {
    import.meta.hot.dispose(stopAll)
  }
}

/** Register an SSE client stop callback; returns unregister. */
export function registerSSEConnection(stop: SSEStopFn): () => void {
  ensureInitialized()
  activeStops.add(stop)
  return () => {
    activeStops.delete(stop)
  }
}

export function stopAllSSEConnections(): void {
  stopAll()
}

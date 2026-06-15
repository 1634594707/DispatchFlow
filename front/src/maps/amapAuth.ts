/** Hostname hints for Amap JS API domain whitelist (Web端). */
export function getAmapWhitelistHosts(): string[] {
  const { hostname, host } = window.location
  const hosts = new Set<string>()
  if (hostname) hosts.add(hostname)
  if (host && host !== hostname) hosts.add(host)
  return [...hosts]
}

export function formatAmapDomainAuthError(reason = 'INVALID_USER_DOMAIN'): string {
  const hosts = getAmapWhitelistHosts()
  const hostList = hosts.map((item) => `「${item}」`).join('、')
  return `高德域名未授权（${reason}）：请在控制台 → 应用管理 → Key 设置 →「Web端(JS API)」域名白名单中加入 ${hostList}，保存后等待 1–2 分钟再刷新。`
}

type AmapMapLike = {
  on: (event: string, handler: () => void) => void
  off?: (event: string, handler: () => void) => void
}

/** Wait until map tiles load or surface domain/key auth failures. */
export function waitForAmapAuth(
  map: AmapMapLike,
  container: HTMLElement,
  timeoutMs = 8000,
): Promise<void> {
  const host = window.location.hostname

  return new Promise((resolve, reject) => {
    let settled = false

    const finishOk = () => {
      if (settled) return
      settled = true
      cleanup()
      resolve()
    }

    const finishErr = (message: string) => {
      if (settled) return
      settled = true
      cleanup()
      reject(new Error(message))
    }

    const onConsoleError = (...args: unknown[]) => {
      const text = args.map(String).join(' ')
      if (text.includes('INVALID_USER_DOMAIN') || text.includes('FlyDataAuthTask error: INVALID_USER_DOMAIN')) {
        finishErr(formatAmapDomainAuthError('INVALID_USER_DOMAIN'))
        return
      }
      if (text.includes('INVALID_USER_KEY') || text.includes('INVALID_USER_SCODE')) {
        finishErr(`高德 Key 或安全密钥无效，请核对 VITE_AMAP_KEY / VITE_AMAP_SECURITY_CODE（当前站点：${host}）`)
      }
    }

    const originalConsoleError = console.error
    console.error = (...args: unknown[]) => {
      onConsoleError(...args)
      originalConsoleError.apply(console, args as Parameters<typeof console.error>)
    }

    const onComplete = () => finishOk()

    map.on('complete', onComplete)

    const timer = window.setTimeout(() => {
      const hasLayer = container.querySelector('.amap-layer') != null
      if (hasLayer) {
        finishOk()
        return
      }
      finishErr(formatAmapDomainAuthError('MAP_LOAD_TIMEOUT'))
    }, timeoutMs)

    const cleanup = () => {
      window.clearTimeout(timer)
      console.error = originalConsoleError
      map.off?.('complete', onComplete)
    }
  })
}

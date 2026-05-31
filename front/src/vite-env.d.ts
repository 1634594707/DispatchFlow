/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_PROXY?: string
  readonly VITE_ADMIN_AUTH_ENABLED?: string
  readonly VITE_MAP_PROVIDER?: string
  readonly VITE_AMAP_KEY?: string
  readonly VITE_AMAP_SECURITY_CODE?: string
  readonly VITE_AMAP_DEFAULT_CENTER?: string
  readonly VITE_AMAP_DEFAULT_ZOOM?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

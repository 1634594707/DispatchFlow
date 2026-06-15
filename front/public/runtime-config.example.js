// Copy to runtime-config.js on the server (same directory as index.html).
// Nginx: add `location = /runtime-config.js { add_header Cache-Control "no-store"; }`
window.__DISPATCHFLOW_RUNTIME_CONFIG__ = {
  VITE_MAP_PROVIDER: 'AMAP',
  VITE_AMAP_KEY: 'your-amap-js-api-key',
  VITE_AMAP_SECURITY_CODE: 'your-amap-security-js-code',
  VITE_AMAP_DEFAULT_CENTER: '121.080354,31.961977',
  VITE_AMAP_DEFAULT_ZOOM: '15',
}

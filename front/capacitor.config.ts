import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'online.aplicity.dispatchflow',
  appName: 'DispatchFlow',
  webDir: 'dist',
  // 远程 URL 模式：APK 内 WebView 直接加载手机端服务
  // 前端代码更新无需重新打包 APK，路由守卫自动跳转到 /mobile/order
  server: {
    url: 'http://64.90.12.129:8081',
    cleartext: true,
  },
  android: {
    allowMixedContent: true,
    captureInput: true,
    webContentsDebuggingEnabled: false,
  },
};

export default config;

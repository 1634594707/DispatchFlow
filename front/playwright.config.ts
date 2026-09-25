import { defineConfig, devices } from '@playwright/test'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const isE2E = process.argv.some(arg => arg.includes('scripts/e2e')) || process.env.npm_lifecycle_event === 'test:e2e'

export default defineConfig({
  testDir: './scripts',
  timeout: 120_000,
  fullyParallel: false,
  retries: 0,
  globalSetup: isE2E
    ? undefined
    : resolve(dirname(fileURLToPath(import.meta.url)), 'scripts/perf/auth.setup.ts'),
  reporter: [['list']],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    // 不给 storageState：perf 的登录态是 sessionStorage，由 scripts/perf/session.ts 注入
    trace: 'off',
    serviceWorkers: 'block',
    screenshot: 'off',
    video: 'off',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        channel: 'chrome',
      },
    },
  ],
})

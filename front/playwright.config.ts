import { defineConfig, devices } from '@playwright/test'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const AUTH_FILE = resolve(dirname(fileURLToPath(import.meta.url)), 'scripts/perf/.auth/admin.json')

export default defineConfig({
  testDir: './scripts/perf',
  timeout: 120_000,
  fullyParallel: false,
  retries: 0,
  globalSetup: resolve(dirname(fileURLToPath(import.meta.url)), 'scripts/perf/auth.setup.ts'),
  reporter: [['list']],
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000',
    storageState: AUTH_FILE,
    trace: 'off',
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

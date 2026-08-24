import { chromium } from '@playwright/test'
import { mkdir } from 'node:fs/promises'

const baseURL = process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000'
const outputDir = 'test-results/visual-redesign'
const ok = (data) => ({ success: true, code: 'OK', message: 'ok', data })
const trend = [
  { label: '周一', totalCount: 42, completedCount: 40, completionRate: 95.2 },
  { label: '周二', totalCount: 51, completedCount: 45, completionRate: 88.2 },
  { label: '周三', totalCount: 48, completedCount: 37, completionRate: 77.1 },
  { label: '周四', totalCount: 56, completedCount: 29, completionRate: 51.8 },
]
const chain = {
  period: 'week',
  avgCompletionMinutes: 16,
  waitP50Minutes: 4,
  waitP90Minutes: 9,
  tasksPerVehiclePerDay: 23,
}
const trackingOrder = {
  orderId: 701,
  orderNo: 'MOB-20260824-701',
  orderStatus: 'IN_PROGRESS',
  taskId: 9001,
  taskNo: 'TSK-9001',
  taskStatus: 'EXECUTING',
  vehicleId: 100,
  vehicleCode: 'VH-001',
  vehicleName: '一号车',
  runtimeStage: 'DELIVERING',
  pickupStation: {
    stationId: 1,
    stationCode: 'ZJF-PICK-01',
    stationName: '取货服务点',
    x: 140,
    y: 140,
    coordLng: 121.1,
    coordLat: 31.9,
    orderable: true,
  },
  dropoffStation: {
    stationId: 2,
    stationCode: 'ZJF-DROP-01',
    stationName: '送货服务点',
    x: 820,
    y: 460,
    coordLng: 121.11,
    coordLat: 31.91,
    orderable: true,
  },
  deliveryZone: 'GEO_DELIVERY',
  weight: 10,
  estimatedArrivalTime: '2026-08-24T20:45:00',
  assignTime: '2026-08-24T20:20:00',
  startTime: '2026-08-24T20:25:00',
  finishTime: null,
  updatedAt: '2026-08-24T20:30:00',
}
const trackingVehicle = {
  parkId: 1,
  vehicleId: 100,
  vehicleCode: 'VH-001',
  vehicleName: '一号车',
  onlineStatus: 'ONLINE',
  dispatchStatus: 'BUSY',
  currentTaskId: 9001,
  currentOrderId: 701,
  batteryLevel: 54,
  x: 460,
  y: 280,
  longitude: 121.105,
  latitude: 31.905,
  heading: 90,
  lastTelemetryAt: '2026-08-24T20:30:00',
  telemetryStale: false,
  runtimeStage: 'DELIVERING',
  targetCode: 'ZJF-DROP-01',
  targetType: 'DROPOFF',
  charging: false,
  lowBattery: false,
  linkMode: 'SIM',
  deliveryZone: 'GEO_DELIVERY',
  trajectory: [],
  geoTrajectory: [],
  plannedRouteGeo: [],
  routeSource: 'LOCAL_GRAPH',
  routeInvalid: true,
}

await mkdir(outputDir, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const failures = []

async function setup(page) {
  const consoleErrors = []
  page.on('pageerror', (error) => consoleErrors.push(error.message))
  page.on('response', (response) => {
    if (response.status() >= 500) {
      consoleErrors.push('HTTP ' + response.status() + ' ' + response.url())
    }
    if (
      response.request().resourceType() === 'script' &&
      response.headers()['content-type']?.includes('text/html') &&
      !response.url().includes('webapi.amap.com')
    ) {
      consoleErrors.push('HTML script ' + response.url())
    }
  })
  page.on('console', (message) => {
    if (
      message.type() === 'error' &&
      !/favicon|manifest|The script has an unsupported MIME type \('text\/html'\)\./i.test(
        message.text(),
      )
    ) {
      consoleErrors.push(message.text())
    }
  })
  await page.addInitScript(() => {
    sessionStorage.setItem('fsd_admin_token', 'visual-admin-token')
    sessionStorage.setItem(
      'fsd_admin_user',
      JSON.stringify({ userId: 1, username: 'visual', role: 'ADMIN', displayName: 'visual' }),
    )
    sessionStorage.setItem('fsd_mobile_api_key', 'visual-mobile-key')
  })
  await page.route('**/api/admin/**', (route) =>
    route.fulfill({ json: ok({ records: [], total: 0, pageNo: 1, pageSize: 10 }) }),
  )
  await page.route(/\/(?:dev-sw|sw)\.js(?:\?.*)?$/, (route) =>
    route.fulfill({
      contentType: 'application/javascript',
      body: 'self.addEventListener("install", () => self.skipWaiting())',
    }),
  )
  await page.route('**/api/admin/sse-ticket', (route) =>
    route.fulfill({ json: ok({ ticket: 'visual-ticket' }) }),
  )
  for (const streamPath of [
    '**/api/admin/fleet/telemetry/stream**',
    '**/api/admin/dispatch/stream**',
  ]) {
    await page.route(streamPath, (route) =>
      route.fulfill({
        contentType: 'text/event-stream',
        body: 'event: connected\\ndata: {}\\n\\n',
      }),
    )
  }
  await page.route('**/api/admin/auth/me', (route) =>
    route.fulfill({
      json: ok({ userId: 1, username: 'visual', role: 'ADMIN', displayName: 'visual' }),
    }),
  )
  await page.route('**/api/admin/parks**', (route) =>
    route.fulfill({ json: ok([{ parkId: 1, parkName: '叠石桥 L1', defaultPark: true }]) }),
  )
  await page.route('**/api/admin/dispatch/workbench**', (route) =>
    route.fulfill({
      json: ok({
        intervention: {
          pendingCount: 2,
          manualPendingCount: 1,
          openExceptionCount: 1,
          pendingTasks: [],
          manualPendingTasks: [],
          openExceptions: [],
        },
        fleetMetrics: {
          assignableVehicleCount: 12,
          pluggedStandbyCount: 4,
          chargingCount: 3,
          onlineVehicleCount: 18,
        },
        parkLayout: { parkId: 1, width: 1000, height: 600, centerLng: 121.1, centerLat: 31.9 },
        vehicles: [
          {
            vehicleId: 100,
            vehicleCode: 'VH-001',
            vehicleName: '一号车',
            onlineStatus: 'ONLINE',
            dispatchStatus: 'IDLE',
            batteryLevel: 88,
          },
          {
            vehicleId: 101,
            vehicleCode: 'VH-002',
            vehicleName: '二号车',
            onlineStatus: 'ONLINE',
            dispatchStatus: 'EXECUTING',
            batteryLevel: 54,
          },
        ],
        pendingTasks: [],
        manualPendingTasks: [],
        openExceptions: [],
      }),
    }),
  )
  await page.route('**/api/admin/park/layout**', (route) =>
    route.fulfill({
      json: ok({ parkId: 1, width: 1000, height: 600, centerLng: 121.1, centerLat: 31.9 }),
    }),
  )
  await page.route('**/api/admin/park/geofences**', (route) => route.fulfill({ json: ok([]) }))
  await page.route('**/api/admin/park/stations**', (route) =>
    route.fulfill({
      json: ok([
        {
          stationId: 1,
          stationCode: 'ZJF-PICK-01',
          stationName: '取货服务点',
          x: 140,
          y: 140,
          coordLng: 121.1,
          coordLat: 31.9,
          orderable: true,
        },
        {
          stationId: 2,
          stationCode: 'ZJF-DROP-01',
          stationName: '送货服务点',
          x: 820,
          y: 460,
          coordLng: 121.11,
          coordLat: 31.91,
          orderable: true,
        },
      ]),
    }),
  )
  await page.route('**/api/admin/park/orders**', (route) => route.fulfill({ json: ok([]) }))
  await page.route('**/api/admin/park/vehicles**', (route) => route.fulfill({ json: ok([]) }))
  await page.route('**/api/admin/park/overview**', (route) =>
    route.fulfill({
      json: ok([
        {
          parkId: 1,
          parkName: '叠石桥 L1',
          parkCode: 'ZJF-L1',
          vehicleCount: 18,
          onlineCount: 16,
          busyCount: 6,
        },
      ]),
    }),
  )
  await page.route('**/api/admin/park/map-versions/active**', (route) =>
    route.fulfill({ json: ok({ versionCode: 'L1-20260824' }) }),
  )
  await page.route('**/api/admin/analytics/efficiency**', (route) =>
    route.fulfill({
      json: ok({
        period: 'week',
        orderCompletionTrend: trend,
        avgTaskDurationMinutes: 16,
        vehicleUtilizationRate: 78.6,
        peakHours: [
          { hour: 9, orderCount: 23, taskCount: 21 },
          { hour: 14, orderCount: 19, taskCount: 18 },
        ],
      }),
    }),
  )
  await page.route('**/api/admin/analytics/exceptions**', (route) =>
    route.fulfill({
      json: ok({
        period: 'week',
        typeDistribution: [
          { type: '路线偏移', count: 4, ratio: 42 },
          { type: '等待超时', count: 3, ratio: 32 },
        ],
        exceptionTrend: trend,
        avgResolutionMinutes: 11,
        rootCauseHints: [],
      }),
    }),
  )
  await page.route('**/api/admin/analytics/daily-summary**', (route) =>
    route.fulfill({
      json: ok({
        date: '2026-08-24',
        orderTotal: 126,
        orderCompleted: 119,
        orderCompletionRate: 94.4,
        taskTotal: 121,
        taskSuccess: 116,
        openExceptionCount: 3,
        resolvedExceptionCount: 9,
        dayOverDayOrderRate: 3.2,
        weekOverWeekOrderRate: -1.4,
        highlightEvents: ['峰值时段 09:00–10:00，车辆利用率 82%', '路线偏移异常已全部分派'],
      }),
    }),
  )
  await page.route('**/api/admin/analytics/chain-kpi**', (route) =>
    route.fulfill({ json: ok(chain) }),
  )
  await page.route('**/api/admin/analytics/peak-compare**', (route) =>
    route.fulfill({
      json: ok({
        normalMode: chain,
        peakMode: { ...chain, avgCompletionMinutes: 21, tasksPerVehiclePerDay: 29 },
      }),
    }),
  )
  await page.route('**/api/admin/analytics/park-comparison**', (route) =>
    route.fulfill({
      json: ok([
        {
          parkId: 1,
          parkName: '叠石桥 L1',
          orderCount: 126,
          taskSuccessCount: 116,
          openExceptionCount: 3,
        },
      ]),
    }),
  )
  return consoleErrors
}

const cases = [
  { name: 'workbench-1440', path: '/workbench', viewport: { width: 1440, height: 900 } },
  { name: 'analytics-1024', path: '/analytics', viewport: { width: 1024, height: 900 } },
  { name: 'workbench-768', path: '/workbench', viewport: { width: 768, height: 1024 } },
  { name: 'mobile-order-375', path: '/mobile/order', viewport: { width: 375, height: 812 } },
  {
    name: 'mobile-tracking-375',
    path: '/mobile/order',
    viewport: { width: 375, height: 812 },
    tracking: true,
  },
  {
    name: 'park-overview-1440',
    path: '/gis/park-overview',
    viewport: { width: 1440, height: 900 },
  },
]

for (const item of cases) {
  const page = await browser.newPage({ viewport: item.viewport, deviceScaleFactor: 1 })
  const errors = await setup(page)
  if (item.tracking) {
    await page.route('**/api/admin/park/orders**', (route) =>
      route.fulfill({ json: ok([trackingOrder]) }),
    )
    await page.route('**/api/admin/park/vehicles**', (route) =>
      route.fulfill({ json: ok([trackingVehicle]) }),
    )
  }
  await page.goto(baseURL + item.path, { waitUntil: 'networkidle', timeout: 30_000 })
  await page.waitForTimeout(700)
  if (item.tracking) {
    const trackingPanel = page.locator('.tracking-panel')
    if ((await trackingPanel.count()) !== 1) {
      failures.push(item.name + ': mobile tracking panel is missing')
    } else {
      await trackingPanel.evaluate((element) => element.scrollIntoView({ block: 'start' }))
      await page.waitForTimeout(120)
    }
  }
  const overflow = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }))
  if (overflow.scrollWidth > overflow.innerWidth + 1) {
    failures.push(
      item.name + ': horizontal overflow ' + overflow.scrollWidth + '/' + overflow.innerWidth,
    )
  }
  if (item.name === 'mobile-order-375') {
    const mobileActionLayout = await page.evaluate(() => {
      const submit = document.querySelector('.submit-bar')?.getBoundingClientRect()
      const submitButton = document.querySelector('.submit-btn')?.getBoundingClientRect()
      const tabBar = document.querySelector('.mobile-tab-bar')?.getBoundingClientRect()
      return {
        submitBottom: submit?.bottom ?? null,
        submitButtonHeight: submitButton?.height ?? null,
        tabTop: tabBar?.top ?? null,
      }
    })
    if (
      mobileActionLayout.submitBottom == null ||
      mobileActionLayout.submitButtonHeight == null ||
      mobileActionLayout.tabTop == null
    ) {
      failures.push('mobile-order-375: fixed action or mobile navigation is missing')
    } else {
      if (mobileActionLayout.submitBottom > mobileActionLayout.tabTop + 1) {
        failures.push('mobile-order-375: fixed action overlaps the mobile navigation bar')
      }
      if (mobileActionLayout.submitButtonHeight < 44) {
        failures.push('mobile-order-375: primary action is below the 44px touch target')
      }
    }
  }
  if (errors.length) failures.push(item.name + ': console ' + errors.join(' | '))
  await page.screenshot({ path: outputDir + '/' + item.name + '.png', fullPage: false })
  console.log(
    item.name +
      ': ' +
      overflow.scrollWidth +
      '/' +
      overflow.innerWidth +
      '; console=' +
      errors.length,
  )
  await page.close()
}

await browser.close()
if (failures.length) {
  console.error(failures.join('\n'))
  process.exitCode = 1
}

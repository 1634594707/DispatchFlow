import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const files = {
  css: await readFile(path.join(root, 'src/styles/tokens.css'), 'utf8'),
  ts: await readFile(path.join(root, 'src/config/tokens.ts'), 'utf8'),
  theme: await readFile(path.join(root, 'src/config/theme.ts'), 'utf8'),
  global: await readFile(path.join(root, 'src/styles/global.less'), 'utf8'),
  legacy: await readFile(path.join(root, 'src/styles/theme.less'), 'utf8'),
  app: await readFile(path.join(root, 'src/App.vue'), 'utf8'),
  policy: await readFile(path.join(root, 'src/config/visualPolicy.ts'), 'utf8'),
}

const sharedUiPaths = [
  'src/components/common/MetricCard.vue',
  'src/components/common/MetricStrip.vue',
  'src/components/common/MetricPanel.vue',
  'src/components/common/QueryToolbar.vue',
  'src/components/common/QueryFilterCard.vue',
  'src/components/common/SkeletonLoader.vue',
  'src/components/common/BackToTop.vue',
  'src/components/common/StatusBadge.vue',
  'src/components/demo/DemoModePanel.vue',
  'src/components/layout/SidebarContent.vue',
  'src/components/layout/NavMenuBadgeIcon.vue',
  'src/components/brand/UserAvatar.vue',
  'src/components/mobile/MobileTabBar.vue',
  'src/components/mobile/MerchantHomePanel.vue',
  'src/components/park/ParkDeliveryOrderModal.vue',
  'src/components/workbench/ParkMiniMap.vue',
  'src/layouts/BasicLayout.vue',
]
const sharedUi = (
  await Promise.all(sharedUiPaths.map((file) => readFile(path.join(root, file), 'utf8')))
).join('\n')
const mapControl = await readFile(path.join(root, 'src/components/map/AmapGeoMap.vue'), 'utf8')
const operationalSurfacePaths = [
  'src/views/workbench/OperationsCockpit.vue',
  'src/views/analytics/Index.vue',
  'src/views/vehicle/Tracking.vue',
  'src/views/gis/ParkOverview.vue',
  'src/views/mobile/ParkOrder.vue',
  'src/views/mobile/MobileOrders.vue',
  'src/views/mobile/MobileProfile.vue',
  'src/components/mobile/QuickOrderPanel.vue',
  'src/components/mobile/OrderTrackingPanel.vue',
  'src/components/analytics/TrendBarChart.vue',
  'src/views/auth/Login.vue',
  'src/views/analytics/ChargingReport.vue',
  'src/views/infrastructure/ChargingPileList.vue',
  'src/views/system/SystemHealth.vue',
  'src/views/system/ReportSchedule.vue',
  'src/views/system/UserList.vue',
  'src/views/digital-twin/Index.vue',
  'src/views/order/Detail.vue',
  'src/views/task/Detail.vue',
  'src/views/vehicle/Detail.vue',
]
const operationalSurfaces = (
  await Promise.all(operationalSurfacePaths.map((file) => readFile(path.join(root, file), 'utf8')))
).join('\n')
const detailViewPaths = [
  'src/views/order/Detail.vue',
  'src/views/task/Detail.vue',
  'src/views/vehicle/Detail.vue',
]
const detailViews = await Promise.all(
  detailViewPaths.map(async (file) => ({
    file,
    source: await readFile(path.join(root, file), 'utf8'),
  })),
)

let assertions = 0
const failures = []

function check(condition, message) {
  assertions += 1
  if (!condition) failures.push(message)
}

function includes(source, value, message) {
  check(source.includes(value), message)
}

function cssVariable(name) {
  return files.css
    .match(new RegExp(`--${name}:\\s*([^;]+);`, 'i'))?.[1]
    .trim()
    .toLowerCase()
}

function channelToLinear(channel) {
  const value = channel / 255
  return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
}

function luminance(hex) {
  const value = hex.replace('#', '')
  const channels = [0, 2, 4].map((index) => Number.parseInt(value.slice(index, index + 2), 16))
  return channels
    .map(channelToLinear)
    .reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0)
}

function contrast(foreground, background) {
  const values = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (values[0] + 0.05) / (values[1] + 0.05)
}

// Required policy controls and protected behavior boundaries.
for (const value of [
  'designVariance: 3',
  'motionIntensity: 2',
  'visualDensity: 8',
  "accentUsage: ['selected', 'action', 'realtime']",
  "'api-contract'",
  "'permission'",
  "'analytics-event'",
  "'map-business-rule'",
]) {
  includes(files.policy, value, `visualPolicy.ts is missing: ${value}`)
}

// CSS and TypeScript must expose the same operational palette.
for (const [cssName, cssValue, tsValue, label] of [
  ['fsd-surface-page', '#08090c', "page: '#08090C'", 'page surface'],
  ['fsd-surface-workspace', '#0e1116', "workspace: '#0E1116'", 'workspace surface'],
  ['fsd-surface-raised', '#151a21', "raised: '#151A21'", 'raised surface'],
  ['fsd-accent', '#56b9c8', "primary: '#56B9C8'", 'interaction accent'],
  ['fsd-action-primary', '#7fd1dd', "action: '#7FD1DD'", 'primary action'],
  ['fsd-success', '#67c587', "success: '#67C587'", 'success semantic'],
  ['fsd-warning', '#e3b65b', "warning: '#E3B65B'", 'warning semantic'],
  ['fsd-error', '#eb7474', "error: '#EB7474'", 'error semantic'],
  ['fsd-info', 'var(--fsd-text-secondary)', 'info: text.secondary', 'neutral information semantic'],
]) {
  check(cssVariable(cssName) === cssValue, `tokens.css has an invalid ${label}`)
  includes(files.ts, tsValue, `tokens.ts has an invalid ${label}`)
}

for (const [name, expected] of [
  ['--fsd-design-variance', '3'],
  ['--fsd-motion-intensity', '2'],
  ['--fsd-visual-density', '8'],
]) {
  check(
    new RegExp(`${name}:\\s*${expected};`).test(files.css),
    `Missing CSS control ${name}: ${expected}`,
  )
}

// Shape, elevation and motion budgets.
for (const [name, expected] of [
  ['fsd-radius-sm', '6px'],
  ['fsd-radius-md', '8px'],
  ['fsd-radius-lg', '10px'],
  ['fsd-shadow-card', 'none'],
  ['fsd-shadow-elevated', '0 16px 36px rgba(0, 0, 0, 0.28)'],
  ['fsd-shadow-popover', '0 16px 36px rgba(0, 0, 0, 0.28)'],
  ['fsd-anim-pulse-glow', 'none'],
  ['fsd-anim-shimmer', 'none'],
]) {
  check(cssVariable(name) === expected, `tokens.css has an invalid ${name}`)
}

for (const match of files.css.matchAll(/--fsd-duration-[a-z-]+:\s*(\d+)ms/g)) {
  const value = Number(match[1])
  check(value >= 120 && value <= 180, `Motion duration is outside 120-180ms: ${match[0]}`)
}

const foundation = [files.css, files.theme, files.global, files.legacy, files.app].join('\n')
for (const [pattern, label] of [
  [/radial-gradient\s*\(/i, 'radial gradient'],
  [/linear-gradient\s*\(/i, 'generic linear gradient'],
  [/backdrop-filter\s*:/i, 'glass blur'],
  [/translateY\s*\(\s*-[^)]+\)/i, 'hover lift'],
  [/animation\s*:[^;\n]*\binfinite\b/i, 'infinite animation'],
  [/box-shadow\s*:[^;\n]*(?:34,\s*211,\s*238|86,\s*185,\s*200)/i, 'cyan glow shadow'],
]) {
  check(!pattern.test(foundation), `Found prohibited foundation effect: ${label}`)
}

for (const [pattern, label] of [
  [/radial-gradient\s*\(/i, 'shared UI radial gradient'],
  [/linear-gradient\s*\(/i, 'shared UI linear gradient'],
  [/backdrop-filter\s*:/i, 'shared UI glass blur'],
  [/animation\s*:[^;\n]*\binfinite\b/i, 'shared UI infinite animation'],
  [/translate(?:X|Y)\s*\(\s*-\d+px/i, 'shared UI positional lift'],
  [/scale\s*\(\s*0\./i, 'shared UI scale feedback'],
  [/transition\s*:\s*all\b/i, 'shared UI broad transition'],
  [
    /\b(?:transition|animation)\s*:\s*[^;]*(?:\d+(?:\.\d+)?s|\d+ms)\b/i,
    'shared UI literal motion duration',
  ],
  [/--fsd-(?:accent-glow|shadow-glow|gradient-)/i, 'deprecated visual token consumption'],
]) {
  check(!pattern.test(sharedUi), `Found prohibited shared effect: ${label}`)
}

for (const [pattern, label] of [
  [/radial-gradient\s*\(/i, 'operational surface radial gradient'],
  [/linear-gradient\s*\(/i, 'operational surface linear gradient'],
  [/create(?:Linear|Radial)Gradient\s*\(/i, 'operational canvas gradient'],
  [/backdrop-filter\s*:/i, 'operational surface glass blur'],
  [/animation\s*:[^;\n]*\binfinite\b/i, 'operational surface infinite animation'],
  [/transition\s*:\s*all\b/i, 'operational surface broad transition'],
  [
    /\b(?:transition|animation)\s*:\s*[^;]*(?:\d+(?:\.\d+)?s|\d+ms)\b/i,
    'operational surface literal motion duration',
  ],
  [
    /--fsd-(?:accent-glow|shadow-glow|gradient-)/i,
    'deprecated operational visual token consumption',
  ],
]) {
  check(!pattern.test(operationalSurfaces), `Found prohibited operational effect: ${label}`)
}

for (const [source, label] of [
  [sharedUi, 'shared UI'],
  [operationalSurfaces, 'operational surface'],
]) {
  for (const match of source.matchAll(/box-shadow\s*:\s*([^;]+);/gi)) {
    const value = match[1].trim()
    check(
      /^(?:none|var\(--fsd-shadow-popover\)(?:\s*!important)?|0\s+16px\s+36px\s+rgba\(0,\s*0,\s*0,\s*0\.28\)(?:\s*!important)?)$/i.test(
        value,
      ),
      `Found unapproved ${label} shadow: ${value}`,
    )
  }
}

check(!/backdrop-filter\s*:/i.test(mapControl), 'Map controls still use glass blur')
check(!/#a855f7/i.test(mapControl), 'Map controls still use a non-semantic purple indicator')

includes(
  files.global,
  'background: var(--fsd-surface-page);',
  'The page background is not a quiet solid surface',
)
includes(
  files.global,
  'box-shadow: none !important;',
  'Ordinary cards or primary buttons still receive a default shadow',
)
includes(files.global, 'transform: none;', 'Hoverable cards do not explicitly cancel lift')
includes(
  files.global,
  '&.ant-btn-disabled',
  'Primary buttons are missing a distinct disabled state',
)
includes(
  files.global,
  '&.ant-btn-dangerous:not(:disabled)',
  'Danger primary actions are not semantic red',
)
includes(
  files.global,
  '.ant-drawer-content-wrapper',
  'Drawers are missing the overlay elevation contract',
)
includes(files.theme, "primaryShadow: 'none'", 'Ant primary buttons still have a shadow')
includes(files.theme, 'rowHoverBg: bg.hover', 'Table hover must remain neutral')
includes(files.theme, 'activeShadow: shadow.focus', 'Inputs need the accessible focus ring')
includes(sharedUi, 'role="search"', 'QueryToolbar must expose a search landmark')
includes(
  sharedUi,
  'border-bottom: 1px solid var(--fsd-border);',
  'Filter toolbar must stay a low-emphasis divider',
)
check(
  sharedUiPaths.includes('src/components/common/MetricStrip.vue'),
  'MetricStrip primitive is missing',
)
check(
  sharedUiPaths.includes('src/components/common/MetricPanel.vue'),
  'MetricPanel primitive is missing',
)
for (const { file, source } of detailViews) {
  check(!/<a-card\b/i.test(source), `${file} still uses equal-weight card containers`)
  check(source.includes('class="detail-summary"'), `${file} is missing its overview strip`)
  check(
    source.includes('class="detail-sections"'),
    `${file} is missing continuous content sections`,
  )
}

for (const file of [
  'src/views/workbench/OperationsCockpit.vue',
  'src/views/vehicle/Tracking.vue',
  'src/views/gis/ParkOverview.vue',
  'src/views/mobile/ParkOrder.vue',
  'src/components/analytics/TrendBarChart.vue',
]) {
  check(
    operationalSurfacePaths.includes(file),
    `Operational surface is missing from the visual guard: ${file}`,
  )
}

check(contrast('#071014', '#7FD1DD') >= 4.5, 'Primary action text does not meet WCAG AA')
for (const surface of ['#08090C', '#0E1116', '#151A21']) {
  check(contrast('#7F8A98', surface) >= 4.5, `Tertiary text does not meet WCAG AA on ${surface}`)
}

if (failures.length > 0) {
  console.error('Visual baseline check failed:')
  failures.forEach((failure) => console.error(`- ${failure}`))
  process.exitCode = 1
} else {
  console.log(`Visual baseline check passed (${assertions} assertions).`)
}

/**
 * DispatchFlow Design Tokens — TypeScript Constants
 * 
 * 与 src/styles/tokens.css 保持同步。修改后两边一起更新。
 * 同步文件：docs/DESIGN-SYSTEM.md
 */

export const bg = {
  deep:     '#0B1018',
  base:     '#121821',
  elevated: '#1A2230',
  hover:    '#222C3C',
  active:   '#283447',
} as const

export const text = {
  primary:   '#EEF3F9',
  secondary: '#9BA8B8',
  tertiary:  '#6B7787',
  heading:   '#F4F8FC',
} as const

export const border = {
  base:   'rgba(255, 255, 255, 0.07)',
  active: 'rgba(255, 255, 255, 0.14)',
  split:  'rgba(255, 255, 255, 0.05)',
} as const

export const accent = {
  primary:   '#22C7E6',
  strong:    '#38D9F2',
  muted:     '#0FA8C6',
  glow:      'rgba(34, 199, 230, 0.16)',
  glowBg:    'rgba(34, 199, 230, 0.10)',
  glowBorder:'rgba(34, 199, 230, 0.30)',
} as const

export const semantic = {
  success: '#2DE08A',
  warning: '#FFC04D',
  error:   '#FF5C7C',
  info:    '#22C7E6',
} as const

export const risk = {
  critical: '#FF5C7C',
  warning:  '#FFC04D',
  active:   '#22C7E6',
  normal:   '#2DE08A',
  muted:    '#6B7787',
} as const

export const space = {
  1:  '4px',
  2:  '8px',
  3:  '12px',
  4:  '16px',
  5:  '20px',
  6:  '24px',
  8:  '32px',
  10: '40px',
  12: '48px',
  16: '64px',
} as const

export const radius = {
  xs: '6px',
  sm: '8px',
  md: '10px',
  lg: '16px',
  xl: '20px',
} as const

export const fontSize = {
  xs:   '12px',
  sm:   '13px',
  base: '14px',
  md:   '15px',
  lg:   '18px',
  xl:   '20px',
  xxl:  '24px',
} as const

export const lineHeight = {
  tight:   '1.3',
  snug:    '1.4',
  normal:  '1.5',
  relaxed: '1.6',
} as const

export const fontWeight = {
  regular:   400,
  medium:    500,
  semibold:  600,
  bold:      700,
  extrabold: 800,
} as const

export const fontFamily = {
  sans: "'Plus Jakarta Sans', 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
  mono: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace",
} as const

export const shadow = {
  card:     '0 1px 2px rgba(0, 0, 0, 0.24), 0 4px 16px rgba(0, 0, 0, 0.18)',
  elevated: '0 12px 36px rgba(0, 0, 0, 0.42), 0 2px 8px rgba(0, 0, 0, 0.28)',
  glow:     '0 0 0 1px rgba(34, 199, 230, 0.16), 0 8px 28px rgba(34, 199, 230, 0.16)',
} as const

export const ease = {
  out:    'cubic-bezier(0.22, 0.61, 0.36, 1)',
  in:     'cubic-bezier(0.55, 0.06, 0.68, 0.19)',
  inOut:  'cubic-bezier(0.65, 0.05, 0.36, 1)',
  bounce: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
} as const

export const duration = {
  fast:   '150ms',
  normal: '250ms',
  slow:   '400ms',
} as const

export const controlHeight = {
  sm: '32px',
  md: '38px',
  lg: '44px',
} as const

export const zIndex = {
  dropdown: 1050,
  sticky:   1020,
  modal:    1000,
  drawer:    990,
  popover:   980,
  tooltip:   970,
  toast:    1060,
} as const

/**
 * DispatchFlow Design Tokens — TypeScript Constants
 * 
 * 与 src/styles/tokens.css 保持同步。修改后两边一起更新。
 * 同步文件：docs/DESIGN-SYSTEM.md
 * 
 * 设计语言：Operational Elegance（Linear/Cursor/Vercel 风格）
 * 主色：Signal Cyan #22D3EE（refined Tailwind cyan-400）
 * 字体：Geist + Geist Mono（与 Codefolio 品牌一致）
 */

export const bg = {
  deep:      '#08090C',
  base:      '#0F1218',
  elevated:  '#161A22',
  hover:     '#1E232D',
  active:    '#262C38',
  spotlight: '#1B2129',
} as const

export const text = {
  primary:   '#F1F5F9',
  secondary: '#94A3B8',
  tertiary:  '#64748B',
  heading:   '#F8FAFC',
  muted:     '#475569',
} as const

export const border = {
  base:    'rgba(241, 245, 249, 0.07)',
  active:  'rgba(241, 245, 249, 0.14)',
  split:   'rgba(241, 245, 249, 0.05)',
  strong:  'rgba(241, 245, 249, 0.22)',
} as const

export const accent = {
  primary:    '#22D3EE',
  strong:     '#67E8F9',
  muted:      '#06B6D4',
  deep:       '#0891B2',
  glow:       'rgba(34, 211, 238, 0.16)',
  glowBg:     'rgba(34, 211, 238, 0.08)',
  glowBorder: 'rgba(34, 211, 238, 0.30)',
  subtle:     'rgba(34, 211, 238, 0.04)',
} as const

export const semantic = {
  success: '#34D399',
  warning: '#FBBF24',
  error:   '#F87171',
  info:    '#22D3EE',
} as const

export const risk = {
  critical: '#F87171',
  warning:  '#FBBF24',
  active:   '#22D3EE',
  normal:   '#34D399',
  muted:    '#64748B',
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
  xs:   '6px',
  sm:   '8px',
  md:   '12px',
  lg:   '16px',
  xl:   '20px',
  xxl:  '24px',
  full: '9999px',
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
  tight:   '1.2',
  snug:    '1.35',
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
  sans:    "'Geist', 'Plus Jakarta Sans', 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
  mono:    "'Geist Mono', 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace",
  display: "'Geist', 'Plus Jakarta Sans', 'PingFang SC', sans-serif",
} as const

export const shadow = {
  card:     '0 1px 2px rgba(0, 0, 0, 0.28), 0 4px 16px rgba(0, 0, 0, 0.20)',
  elevated: '0 12px 36px rgba(0, 0, 0, 0.48), 0 2px 8px rgba(0, 0, 0, 0.32)',
  glow:     '0 0 0 1px rgba(34, 211, 238, 0.16), 0 8px 28px rgba(34, 211, 238, 0.20)',
  soft:     '0 1px 2px rgba(0, 0, 0, 0.18)',
  popover:  '0 12px 32px rgba(0, 0, 0, 0.48), 0 0 0 1px rgba(241, 245, 249, 0.06)',
} as const

export const ease = {
  out:    'cubic-bezier(0.22, 0.61, 0.36, 1)',
  in:     'cubic-bezier(0.55, 0.06, 0.68, 0.19)',
  inOut:  'cubic-bezier(0.65, 0.05, 0.36, 1)',
  bounce: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  spring: 'cubic-bezier(0.16, 1, 0.3, 1)',
} as const

export const duration = {
  fast:   '150ms',
  base:   '220ms',
  normal: '280ms',
  slow:   '400ms',
} as const

export const controlHeight = {
  sm: '32px',
  md: '40px',
  lg: '48px',
} as const

export const zIndex = {
  dropdown: 1050,
  sticky:   1020,
  modal:    1000,
  drawer:    990,
  popover:   980,
  tooltip:   970,
  toast:    1060,
  header:    100,
  fab:        90,
} as const

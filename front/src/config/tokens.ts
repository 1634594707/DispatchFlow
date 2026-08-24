/**
 * DispatchFlow operational design tokens.
 * Keep values synchronized with src/styles/tokens.css.
 */

export const visualControls = {
  designVariance: 3,
  motionIntensity: 2,
  visualDensity: 8,
} as const

export const surface = {
  page: '#08090C',
  workspace: '#0E1116',
  raised: '#151A21',
  overlay: '#151A21',
  status: '#11161C',
  hover: '#1A2028',
  active: '#202833',
} as const

export const bg = {
  deep: surface.page,
  base: surface.workspace,
  elevated: surface.raised,
  hover: surface.hover,
  active: surface.active,
  spotlight: surface.status,
} as const

export const text = {
  primary: '#EEF2F6',
  secondary: '#A3ADBA',
  tertiary: '#7F8A98',
  heading: '#F5F7FA',
  muted: '#687482',
  onAction: '#071014',
} as const

export const border = {
  base: 'rgba(238, 242, 246, 0.10)',
  active: 'rgba(238, 242, 246, 0.18)',
  split: 'rgba(238, 242, 246, 0.07)',
  strong: 'rgba(238, 242, 246, 0.26)',
} as const

export const accent = {
  primary: '#56B9C8',
  strong: '#7FD1DD',
  muted: '#438F9B',
  deep: '#326F78',
  selected: 'rgba(86, 185, 200, 0.12)',
  selectedSubtle: 'rgba(86, 185, 200, 0.06)',
  selectedBorder: 'rgba(86, 185, 200, 0.38)',
  action: '#7FD1DD',
  actionHover: '#91D9E2',
  actionActive: '#68C4D1',

  // Compatibility aliases. These are flat state fills, not glow effects.
  glow: 'rgba(86, 185, 200, 0.12)',
  glowBg: 'rgba(86, 185, 200, 0.12)',
  glowBorder: 'rgba(86, 185, 200, 0.38)',
  subtle: 'rgba(86, 185, 200, 0.06)',
} as const

export const semantic = {
  success: '#67C587',
  warning: '#E3B65B',
  error: '#EB7474',
  info: text.secondary,
} as const

export const semanticSurface = {
  success: 'rgba(103, 197, 135, 0.12)',
  warning: 'rgba(227, 182, 91, 0.12)',
  error: 'rgba(235, 116, 116, 0.12)',
  neutral: 'rgba(163, 173, 186, 0.08)',
} as const

export const guardPalette = {
  accent: '#5E9BAB',
  success: '#4A9A75',
  warning: '#C29440',
  error: '#C45868',
  info: '#8B98A6',
} as const

export const risk = {
  critical: semantic.error,
  warning: semantic.warning,
  active: accent.primary,
  normal: semantic.success,
  muted: text.muted,
} as const

export const space = {
  1: '4px',
  2: '8px',
  3: '12px',
  4: '16px',
  5: '20px',
  6: '24px',
  8: '32px',
  10: '40px',
  12: '48px',
  16: '64px',
} as const

export const radius = {
  xs: '6px',
  sm: '6px',
  md: '8px',
  lg: '10px',
  xl: '10px',
  xxl: '10px',
  full: '9999px',
} as const

export const fontSize = {
  xs: '12px',
  sm: '13px',
  base: '14px',
  md: '15px',
  lg: '18px',
  xl: '20px',
  xxl: '24px',
} as const

export const lineHeight = {
  tight: '1.2',
  snug: '1.35',
  normal: '1.5',
  relaxed: '1.6',
} as const

export const fontWeight = {
  regular: 400,
  medium: 500,
  semibold: 600,
  bold: 700,
  extrabold: 800,
} as const

export const fontFamily = {
  sans: "'Geist', 'Plus Jakarta Sans', 'PingFang SC', 'Microsoft YaHei', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
  mono: "'Geist Mono', 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace",
  display: "'Geist', 'Plus Jakarta Sans', 'PingFang SC', sans-serif",
} as const

export const shadow = {
  card: 'none',
  elevated: '0 16px 36px rgba(0, 0, 0, 0.28)',
  glow: 'none',
  soft: 'none',
  popover: '0 16px 36px rgba(0, 0, 0, 0.28)',
  focus: '0 0 0 2px rgba(127, 209, 221, 0.34)',
} as const

export const ease = {
  out: 'cubic-bezier(0.2, 0, 0, 1)',
  in: 'cubic-bezier(0.4, 0, 1, 1)',
  inOut: 'cubic-bezier(0.4, 0, 0.2, 1)',
  bounce: 'cubic-bezier(0.2, 0, 0, 1)',
  spring: 'cubic-bezier(0.2, 0, 0, 1)',
} as const

export const duration = {
  fast: '120ms',
  base: '150ms',
  normal: '180ms',
  slow: '180ms',
} as const

export const controlHeight = {
  sm: '32px',
  md: '40px',
  lg: '48px',
} as const

export const zIndex = {
  dropdown: 1050,
  sticky: 1020,
  modal: 1000,
  drawer: 990,
  popover: 980,
  tooltip: 970,
  toast: 1060,
  header: 100,
  fab: 90,
} as const

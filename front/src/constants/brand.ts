/** DispatchFlow 视觉标识色板（见产品视觉规范） */
export const BRAND_COLORS = {
  primary: '#1E88E5',
  primaryDark: '#1565C0',
  primaryLight: '#42A5F5',
  primaryDeep: '#0D47A1',
  onPrimary: '#FFFFFF',
  textPrimary: '#333333',
  textSecondary: '#666666',
} as const

export const BRAND_AVATAR_GRADIENTS = {
  default: `linear-gradient(135deg, ${BRAND_COLORS.primaryLight} 0%, ${BRAND_COLORS.primaryDeep} 100%)`,
  admin: `linear-gradient(135deg, ${BRAND_COLORS.primaryLight} 0%, ${BRAND_COLORS.primaryDeep} 100%)`,
  operator: 'linear-gradient(135deg, #66BB6A 0%, #2E7D32 100%)',
  fieldOps: 'linear-gradient(135deg, #FFA726 0%, #EF6C00 100%)',
  viewer: 'linear-gradient(135deg, #90A4AE 0%, #546E7A 100%)',
} as const

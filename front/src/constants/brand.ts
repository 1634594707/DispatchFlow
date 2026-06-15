/** DispatchFlow 视觉标识色板（与暗色指挥中心主题统一，青蓝色系） */
export const BRAND_COLORS = {
  primary: '#22C7E6',
  primaryDark: '#2D8BF0',
  primaryLight: '#38D9F2',
  primaryDeep: '#0FA8C6',
  onPrimary: '#04121A',
  textPrimary: '#EEF3F9',
  textSecondary: '#9BA8B8',
} as const

export const BRAND_AVATAR_GRADIENTS = {
  default: `linear-gradient(135deg, ${BRAND_COLORS.primaryLight} 0%, ${BRAND_COLORS.primaryDeep} 100%)`,
  admin: `linear-gradient(135deg, ${BRAND_COLORS.primaryLight} 0%, ${BRAND_COLORS.primaryDeep} 100%)`,
  operator: 'linear-gradient(135deg, #66BB6A 0%, #2E7D32 100%)',
  fieldOps: 'linear-gradient(135deg, #FFA726 0%, #EF6C00 100%)',
  viewer: 'linear-gradient(135deg, #90A4AE 0%, #546E7A 100%)',
} as const

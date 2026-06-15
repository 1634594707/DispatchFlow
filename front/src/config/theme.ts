/**
 * DispatchFlow Design System — Ant Design Vue Theme Tokens
 * 
 * 设计权威来源：docs/DESIGN-SYSTEM.md
 * CSS 令牌同步源：src/styles/tokens.css → src/config/tokens.ts
 * 主色调：信号青 #22C7E6（暗色指挥中心风格）
 * 字体排印：Plus Jakarta Sans + JetBrains Mono
 * 间距基线：4px
 */

import { theme } from 'ant-design-vue'
import {
  bg, text, border, accent, semantic,
  shadow, ease, controlHeight, fontFamily, fontWeight, radius, lineHeight, space, fontSize,
} from '@/config/tokens'

const { darkAlgorithm } = theme

// ── Theme Token Configuration ──────────────────────────────
export const themeConfig = {
  algorithm: darkAlgorithm,

  token: {
    // ── Brand (from tokens.ts accent) ────────────────────
    colorPrimary:        accent.primary,
    colorPrimaryHover:   accent.muted,
    colorPrimaryActive:  '#0B8299',
    colorPrimaryBg:      accent.glowBg,
    colorPrimaryBgHover: accent.glow,
    colorPrimaryBorder:  accent.glowBorder,
    colorPrimaryText:    accent.primary,
    colorPrimaryTextHover:   accent.strong,
    colorPrimaryTextActive:  accent.muted,

    // ── Semantic (from tokens.ts semantic) ───────────────
    colorSuccess:  semantic.success,
    colorWarning:  semantic.warning,
    colorError:    semantic.error,
    colorInfo:     semantic.info,

    // ── Background (from tokens.ts bg) ───────────────────
    colorBgBase:       bg.base,
    colorBgContainer:  bg.base,
    colorBgElevated:   bg.elevated,
    colorBgLayout:     bg.deep,
    colorBgSpotlight:  bg.elevated,
    colorBgMask:       'rgba(6, 9, 15, 0.55)',

    // ── Text (from tokens.ts text) ───────────────────────
    colorTextBase:       text.primary,
    colorText:           text.primary,
    colorTextSecondary:  text.secondary,
    colorTextTertiary:   text.tertiary,
    colorTextQuaternary: 'rgba(154, 168, 184, 0.45)',

    // ── Border (from tokens.ts border) ───────────────────
    colorBorder:        border.base,
    colorBorderSecondary: border.split,
    colorSplit:         border.split,

    // ── Typography (from tokens.ts) ──────────────────────
    fontFamily:      fontFamily.sans,
    fontFamilyCode:  fontFamily.mono,
    fontSize:        14,
    fontSizeSM:      12,
    fontSizeLG:      16,
    fontSizeXL:      20,
    fontSizeHeading1: 24,
    fontSizeHeading2: 20,
    fontSizeHeading3: 18,
    fontSizeHeading4: 15,
    fontSizeHeading5: 14,
    lineHeight:      parseFloat(lineHeight.relaxed),
    fontWeightStrong: fontWeight.semibold,

    // ── Border Radius (from tokens.ts radius) ────────────
    borderRadius:    parseInt(radius.md),
    borderRadiusSM:  parseInt(radius.sm),
    borderRadiusLG:  parseInt(radius.lg),
    borderRadiusXS:  parseInt(radius.xs),

    // ── Spacing / Padding (from tokens.ts space) ─────────
    paddingXS:     parseInt(space['1']),
    paddingSM:     parseInt(space['2']),
    padding:       parseInt(space['3']),
    paddingMD:     parseInt(space['4']),
    paddingLG:     parseInt(space['6']),
    paddingXL:     parseInt(space['8']),
    marginXS:      parseInt(space['1']),
    marginSM:      parseInt(space['2']),
    margin:        parseInt(space['3']),
    marginMD:      parseInt(space['4']),
    marginLG:      parseInt(space['6']),
    marginXL:      parseInt(space['8']),

    // ── Motion (from tokens.ts ease/duration) ────────────
    motionUnit:    0.1,
    motionBase:    0,
    motionEaseOut:   ease.out,
    motionEaseIn:    ease.in,
    motionEaseInOut: ease.inOut,

    // ── Size (from tokens.ts controlHeight) ──────────────
    controlHeight:    parseInt(controlHeight.md),
    controlHeightSM:  parseInt(controlHeight.sm),
    controlHeightLG:  parseInt(controlHeight.lg),
    controlHeightXS:  24,

    // ── Interactive ──────────────────────────────────────
    wireframe: false,
    lineWidth:    1,
    lineWidthBold: 2,
    controlOutline:    accent.glow,
    controlOutlineWidth: 3,
    colorLink:    accent.strong,
    colorLinkHover: '#B3EDF7',
    colorLinkActive: accent.muted,
  },

  // ── Component-Specific Tokens ────────────────────────────
  components: {
    Button: {
      colorPrimary:        accent.primary,
      colorPrimaryHover:   accent.muted,
      colorPrimaryActive:  '#0B8299',
      primaryShadow:       '0 2px 10px rgba(34, 199, 230, 0.24)',
      fontWeight:          fontWeight.semibold,
      defaultBg:           bg.elevated,
      defaultBorderColor:  border.base,
      defaultColor:        text.primary,
      defaultHoverBorderColor: accent.primary,
      defaultHoverColor:   accent.primary,
      defaultHoverBg:      bg.hover,
      borderRadius:        parseInt(radius.sm),
      borderRadiusSM:      parseInt(radius.xs),
      borderRadiusLG:      parseInt(radius.md),
      paddingInline:       20,
      paddingInlineSM:     14,
      paddingInlineLG:     24,
      contentFontSize:     parseInt(fontSize.sm),
      contentFontSizeSM:   parseInt(fontSize.xs),
      contentFontSizeLG:   parseInt(fontSize.base),
      controlHeight:       parseInt(controlHeight.md),
      controlHeightSM:     parseInt(controlHeight.sm),
      controlHeightLG:     parseInt(controlHeight.lg),
    },

    Table: {
      headerBg:           bg.elevated,
      headerColor:        text.secondary,
      headerSplitColor:   border.base,
      rowHoverBg:         'rgba(34, 199, 230, 0.05)',
      rowSelectedBg:      'rgba(34, 199, 230, 0.08)',
      rowExpandedBg:      bg.elevated,
      borderColor:        border.base,
      cellPaddingBlock:   12,
      cellPaddingInline:  16,
      cellPaddingBlockMD: 10,
      cellPaddingInlineMD:12,
      cellPaddingBlockSM:  8,
      cellPaddingInlineSM:10,
      fontSize:           13,
      headerFontSizeSM:   12,
      footerBg:           bg.base,
    },

    Card: {
      colorBgContainer:   bg.base,
      colorBorderSecondary: border.base,
      borderRadius:       parseInt(radius.lg),
      borderRadiusLG:     parseInt(radius.xl),
      padding:            24,
      paddingLG:          32,
      boxShadow:          shadow.card,
      boxShadowTertiary:  shadow.elevated,
    },

    Menu: {
      darkItemBg:           bg.base,
      darkSubMenuItemBg:    bg.base,
      darkItemColor:        text.secondary,
      darkItemHoverColor:   text.primary,
      darkItemHoverBg:      bg.hover,
      darkItemSelectedBg:   accent.glow,
      darkItemSelectedColor: accent.primary,
      darkGroupTitleColor:  text.tertiary,
      darkItemBorderRadius:  parseInt(radius.md),
      itemMarginInline:     8,
      itemMarginBlock:      2,
      itemHeight:           44,
    },

    Input: {
      colorBgContainer:    bg.elevated,
      colorBorder:         border.base,
      colorText:           text.primary,
      colorTextPlaceholder: text.tertiary,
      hoverBorderColor:    accent.primary,
      activeBorderColor:   accent.primary,
      activeShadow:        '0 0 0 3px ' + accent.glow,
      borderRadius:        parseInt(radius.md),
      borderRadiusSM:      parseInt(radius.sm),
      borderRadiusLG:      12,
      controlHeight:       parseInt(controlHeight.md),
      controlHeightSM:     parseInt(controlHeight.sm),
      controlHeightLG:     parseInt(controlHeight.lg),
      paddingBlock:         8,
      paddingInline:       12,
    },

    Select: {
      colorBgContainer:    bg.elevated,
      colorBorder:         border.base,
      hoverBorderColor:    accent.primary,
      activeOutlineColor:  accent.glow,
      optionSelectedBg:    accent.glowBg,
      optionSelectedColor: accent.primary,
      borderRadius:        parseInt(radius.md),
      controlHeight:       parseInt(controlHeight.md),
      selectorBg:          bg.elevated,
    },

    Tag: {
      defaultBg:          bg.elevated,
      defaultColor:       text.secondary,
      borderRadiusSM:     parseInt(radius.sm),
      fontSizeSM:         12,
    },

    Modal: {
      colorBgElevated:    bg.base,
      colorBorder:        border.base,
      borderRadiusLG:     parseInt(radius.lg),
      headerBg:           'transparent',
      contentBg:          bg.base,
      titleColor:         text.heading,
      titleFontSize:      18,
      boxShadow:          shadow.elevated,
    },

    Drawer: {
      colorBgElevated:    bg.base,
      colorBorder:        border.base,
      borderRadiusLG:     parseInt(radius.lg),
    },

    Breadcrumb: {
      colorText:          text.tertiary,
      lastItemColor:      text.primary,
      linkColor:          text.secondary,
      linkHoverColor:     accent.primary,
      separatorColor:     text.tertiary,
    },

    Pagination: {
      colorBgContainer:   bg.elevated,
      colorPrimary:       accent.primary,
      itemActiveBg:       accent.glow,
      itemSize:           32,
    },

    Badge: {
      colorSuccess:     semantic.success,
      colorError:       semantic.error,
      colorWarning:     semantic.warning,
      colorProcessing:  accent.primary,
      statusSize:       8,
    },

    Tooltip: {
      colorBgSpotlight:   bg.elevated,
      colorTextLightSolid: text.primary,
      borderRadius:       parseInt(radius.sm),
    },

    Progress: {
      defaultColor:       accent.primary,
      remainingColor:     accent.glow,
      circleTextColor:    text.primary,
    },

    Tabs: {
      colorText:          text.secondary,
      itemActiveColor:    accent.primary,
      itemHoverColor:     text.primary,
      inkBarColor:        accent.primary,
      horizontalItemGutter: 32,
    },

    Notification: {
      colorBgElevated:    bg.elevated,
      colorText:          text.primary,
      colorTextHeading:   text.heading,
      borderRadiusLG:     12,
    },

    Layout: {
      bodyBg:             bg.deep,
      headerBg:           bg.base,
      siderBg:            bg.base,
      triggerBg:          bg.elevated,
      triggerColor:       text.secondary,
    },
  },
}

/**
 * Guard Mode — 低对比度值守模式
 * 适合长时间监控场景，减少视觉疲劳
 */
export const guardModeTokens = {
  token: {
    colorPrimary:    '#4A9EB3',
    colorSuccess:    '#3D9A6A',
    colorWarning:    '#B8892A',
    colorError:      '#C43D5A',
    colorInfo:       '#4A9EB3',
  },
}

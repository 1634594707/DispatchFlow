<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :aria-label="ariaLabel"
    role="img"
  >
    <defs>
      <linearGradient :id="gradId" x1="2" y1="2" x2="30" y2="30" gradientUnits="userSpaceOnUse">
        <stop :stop-color="BRAND_COLORS.primary" />
        <stop offset="1" :stop-color="BRAND_COLORS.primaryDark" />
      </linearGradient>
    </defs>
    <!-- 圆角外框 -->
    <rect width="32" height="32" rx="8" :fill="`url(#${gradId})`" />
    <!-- 底部弯曲路径（左右各留 4px） -->
    <path
      d="M4 22 Q16 11 28 22"
      stroke="#FFFFFF"
      stroke-width="2"
      stroke-linecap="round"
      fill="none"
    />
    <!-- 流动弧线：构成抽象字母 D，交汇于路径最高点 -->
    <path
      d="M16 17 Q11 13.5 16 11"
      stroke="#FFFFFF"
      stroke-width="2"
      stroke-linecap="round"
      fill="none"
    />
    <path
      d="M16 17 Q21 13.5 16 11"
      stroke="#FFFFFF"
      stroke-width="2"
      stroke-linecap="round"
      fill="none"
    />
    <!-- AGV：圆角矩形车身（宽约外框 1/3）+ 双轮 -->
    <rect x="10.5" y="15.5" width="11" height="5" rx="1.5" fill="#FFFFFF" />
    <circle cx="12.5" cy="21" r="1.5" fill="#FFFFFF" />
    <circle cx="19.5" cy="21" r="1.5" fill="#FFFFFF" />
  </svg>
</template>

<script setup lang="ts">
import { useId } from 'vue'
import { BRAND_COLORS } from '@/constants/brand'

withDefaults(
  defineProps<{
    size?: number | string
    ariaLabel?: string
  }>(),
  {
    size: 32,
    ariaLabel: 'DispatchFlow',
  },
)

const uid = useId().replace(/:/g, '')
const gradId = `df-logo-bg-${uid}`
</script>

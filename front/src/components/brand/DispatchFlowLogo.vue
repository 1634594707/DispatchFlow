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
      <linearGradient :id="gradId" x1="3" y1="3" x2="29" y2="29" gradientUnits="userSpaceOnUse">
        <stop :stop-color="BRAND_COLORS.primary" />
        <stop offset="1" :stop-color="BRAND_COLORS.primaryDark" />
      </linearGradient>
    </defs>
    <!-- 圆角底板 -->
    <rect width="32" height="32" rx="8" :fill="`url(#${gradId})`" />
    <!-- 流动尾迹：由细到粗的运动轨迹 -->
    <path
      d="M6 24 C11 22 13 18 17.5 14"
      stroke="#FFFFFF"
      stroke-width="2.4"
      stroke-linecap="round"
      fill="none"
      opacity="0.95"
    />
    <path
      d="M9 26 C12.5 24.5 14 22 16.5 19"
      stroke="#FFFFFF"
      stroke-width="2"
      stroke-linecap="round"
      fill="none"
      opacity="0.55"
    />
    <!-- 调度单元：前导节点 -->
    <circle cx="21" cy="11" r="4" fill="#FFFFFF" />
    <circle cx="21" cy="11" r="1.7" :fill="BRAND_COLORS.primaryDark" />
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

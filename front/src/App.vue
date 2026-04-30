<template>
  <a-config-provider :theme="themeConfig">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { theme } from 'ant-design-vue'

const themeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#00B4D8',
    colorSuccess: '#00E676',
    colorWarning: '#FFB020',
    colorError: '#FF3D71',
    colorBgBase: '#0D1117',
    colorTextBase: '#E6EDF3',
    borderRadius: 8,
    fontFamily: "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif",
  },
}
</script>

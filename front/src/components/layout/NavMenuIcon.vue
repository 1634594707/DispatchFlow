<template>
  <span class="nav-menu-icon">
    <component :is="iconComponent" />
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  DesktopOutlined,
  DashboardOutlined,
  FileTextOutlined,
  CarOutlined,
  AlertOutlined,
  LineChartOutlined,
  ThunderboltOutlined,
  HeatMapOutlined,
  ApartmentOutlined,
  ClusterOutlined,
  ToolOutlined,
  DeploymentUnitOutlined,
  SettingOutlined,
  TeamOutlined,
  AuditOutlined,
  NodeIndexOutlined,
  ApiOutlined,
  HeartOutlined,
  ScheduleOutlined,
  SafetyOutlined,
  BellOutlined,
  FundProjectionScreenOutlined,
  PieChartOutlined,
} from '@ant-design/icons-vue'
import type { NavIcon } from '@/config/navigation'

const props = defineProps<{
  icon?: NavIcon
}>()

const ICON_MAP: Record<NavIcon, object> = {
  workbench: DesktopOutlined,
  dispatchOps: FundProjectionScreenOutlined,
  dashboard: DashboardOutlined,
  orders: FileTextOutlined,
  tasks: CarOutlined,
  exceptions: AlertOutlined,
  monitor: PieChartOutlined,
  analytics: LineChartOutlined,
  charging: ThunderboltOutlined,
  tracking: HeatMapOutlined,
  fleet: CarOutlined,
  vehicles: CarOutlined,
  infrastructure: ApartmentOutlined,
  vertical: ClusterOutlined,
  fieldOps: ToolOutlined,
  digitalTwin: DeploymentUnitOutlined,
  system: SettingOutlined,
  users: TeamOutlined,
  logs: AuditOutlined,
  strategy: NodeIndexOutlined,
  integration: ApiOutlined,
  health: HeartOutlined,
  report: ScheduleOutlined,
  security: SafetyOutlined,
  alert: BellOutlined,
}

const iconComponent = computed(() => {
  if (!props.icon) return DashboardOutlined
  return ICON_MAP[props.icon] ?? DashboardOutlined
})
</script>

<style scoped lang="less">
.nav-menu-icon {
  display: inline-grid;
  width: 20px;
  height: 20px;
  place-items: center;
  line-height: 0;
}

.nav-menu-icon :deep(.anticon) {
  display: inline-grid;
  width: 20px;
  height: 20px;
  place-items: center;
  line-height: 0;
}

.nav-menu-icon :deep(svg) {
  display: block;
  width: 16px;
  height: 16px;
  margin: 0;
  overflow: visible;
}
</style>

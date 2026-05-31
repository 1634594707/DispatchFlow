<template>
  <component :is="iconComponent" />
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

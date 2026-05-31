import type { AdminUser } from '@/types/auth'

export type NavRole = AdminUser['role']
export type NavBadge = 'workbench' | 'exceptions'

export type NavIcon =
  | 'workbench'
  | 'dispatchOps'
  | 'dashboard'
  | 'orders'
  | 'tasks'
  | 'exceptions'
  | 'monitor'
  | 'analytics'
  | 'charging'
  | 'tracking'
  | 'fleet'
  | 'vehicles'
  | 'infrastructure'
  | 'vertical'
  | 'fieldOps'
  | 'digitalTwin'
  | 'system'
  | 'users'
  | 'logs'
  | 'strategy'
  | 'integration'
  | 'health'
  | 'report'
  | 'security'
  | 'alert'

export interface NavItem {
  key: string
  label: string
  icon?: NavIcon
  path?: string
  roles?: NavRole[]
  badge?: NavBadge
  children?: NavItem[]
  /** Replace group with sole visible child (e.g. FIELD_OPS → 现场工单) */
  promoteSingleChild?: boolean
  commandGroup?: string
}

const STAFF_ROLES: NavRole[] = ['ADMIN', 'OPERATOR', 'VIEWER']
const ADMIN_ROLES: NavRole[] = ['ADMIN']

export const NAVIGATION_TREE: NavItem[] = [
  {
    key: 'workbench',
    label: '调度工作台',
    icon: 'workbench',
    path: '/workbench',
    roles: STAFF_ROLES,
    badge: 'workbench',
    commandGroup: '导航',
  },
  {
    key: 'dispatch-ops',
    label: '调度运营',
    icon: 'dispatchOps',
    roles: STAFF_ROLES,
    commandGroup: '调度运营',
    children: [
      { key: 'dashboard', label: '调度看板', path: '/dashboard', icon: 'dashboard' },
      { key: 'orders', label: '订单管理', path: '/orders', icon: 'orders' },
      { key: 'tasks', label: '调度任务', path: '/tasks', icon: 'tasks' },
      {
        key: 'exceptions',
        label: '异常任务',
        path: '/exceptions',
        icon: 'exceptions',
        badge: 'exceptions',
      },
    ],
  },
  {
    key: 'analytics-monitor',
    label: '分析监控',
    icon: 'monitor',
    roles: STAFF_ROLES,
    commandGroup: '分析监控',
    children: [
      { key: 'analytics', label: '运营分析', path: '/analytics', icon: 'analytics' },
      { key: 'analytics-charging', label: '充电报表', path: '/analytics/charging', icon: 'charging' },
      { key: 'vehicle-tracking', label: '车辆监控大屏', path: '/vehicle-tracking', icon: 'tracking' },
      { key: 'park-overview', label: '多园区总览', path: '/gis/park-overview', icon: 'tracking' },
    ],
  },
  {
    key: 'fleet',
    label: '车队资源',
    icon: 'fleet',
    roles: STAFF_ROLES,
    commandGroup: '车队资源',
    children: [
      { key: 'vehicles', label: '车辆管理', path: '/vehicles', icon: 'vehicles' },
    ],
  },
  {
    key: 'infrastructure',
    label: '基础设施',
    icon: 'infrastructure',
    roles: ADMIN_ROLES,
    commandGroup: '基础设施',
    children: [
      { key: 'infra-parks', label: '园区管理', path: '/infrastructure/parks' },
      { key: 'infra-stations', label: '站点管理', path: '/infrastructure/stations' },
      { key: 'infra-parking-slots', label: '停车位管理', path: '/infrastructure/parking-slots' },
      { key: 'infra-charging-piles', label: '充电桩管理', path: '/infrastructure/charging-piles' },
      { key: 'infra-swap-cabinets', label: '换电柜管理', path: '/infrastructure/swap-cabinets' },
      { key: 'infra-road-network', label: '路网管理', path: '/infrastructure/road-network' },
      { key: 'infra-traffic', label: '交通态势', path: '/infrastructure/traffic' },
      { key: 'infra-geofences', label: '地理围栏', path: '/infrastructure/geofences' },
    ],
  },
  {
    key: 'vertical-ops',
    label: '产业运营',
    icon: 'vertical',
    roles: ['ADMIN', 'FIELD_OPS'],
    promoteSingleChild: true,
    commandGroup: '产业运营',
    children: [
      {
        key: 'vertical-routes',
        label: '线路管理',
        path: '/vertical/routes',
        roles: ADMIN_ROLES,
      },
      { key: 'vertical-hub', label: '母港分流', path: '/vertical/hub', roles: ADMIN_ROLES },
      { key: 'vertical-peak', label: '高峰预案', path: '/vertical/peak-mode', roles: ADMIN_ROLES },
      {
        key: 'vertical-rules',
        label: '自动化规则',
        path: '/vertical/automation-rules',
        roles: ADMIN_ROLES,
      },
      { key: 'vertical-ops', label: '运维快照', path: '/vertical/ops-snapshot', roles: ADMIN_ROLES },
      {
        key: 'field-ops-tickets',
        label: '现场工单',
        path: '/field-ops/tickets',
        icon: 'fieldOps',
        roles: ['ADMIN', 'FIELD_OPS'],
      },
    ],
  },
  {
    key: 'digital-twin',
    label: '数字孪生',
    icon: 'digitalTwin',
    path: '/digital-twin',
    roles: STAFF_ROLES,
    commandGroup: '导航',
  },
  {
    key: 'system',
    label: '系统管理',
    icon: 'system',
    roles: ADMIN_ROLES,
    commandGroup: '系统管理',
    children: [
      { key: 'system-users', label: '用户管理', path: '/system/users', icon: 'users' },
      { key: 'system-operate-logs', label: '操作日志', path: '/system/operate-logs', icon: 'logs' },
      {
        key: 'system-dispatch-strategy',
        label: '调度策略',
        path: '/system/dispatch-strategy',
        icon: 'strategy',
      },
      {
        key: 'system-integration',
        label: '外部集成',
        path: '/system/integration',
        icon: 'integration',
      },
      { key: 'system-report-schedule', label: '定时报表', path: '/system/report-schedule', icon: 'report' },
      { key: 'system-security', label: '安全设置', path: '/system/security', icon: 'security' },
      {
        key: 'system-alert-settings',
        label: '告警设置',
        path: '/system/alert-settings',
        icon: 'alert',
      },
      { key: 'system-health', label: '系统健康', path: '/system/health', icon: 'health' },
    ],
  },
]

export interface NavCommandItem {
  key: string
  label: string
  hint: string
  group: string
  path: string
}

function itemRoles(item: NavItem, inherited?: NavRole[]): NavRole[] {
  return item.roles ?? inherited ?? STAFF_ROLES
}

export function filterNavByRole(
  items: NavItem[],
  role: NavRole | null | undefined,
  inheritedRoles?: NavRole[],
): NavItem[] {
  if (!role) return []

  const result: NavItem[] = []
  for (const item of items) {
    const roles = itemRoles(item, inheritedRoles)
    if (!roles.includes(role)) continue

    if (item.children?.length) {
      const children = filterNavByRole(item.children, role, roles)
      if (children.length === 0) continue

      if (item.promoteSingleChild && children.length === 1) {
        const child = children[0]
        result.push({
          ...child,
          icon: child.icon ?? item.icon,
          commandGroup: child.commandGroup ?? item.commandGroup,
        })
        continue
      }

      result.push({ ...item, children })
      continue
    }

    if (item.path) {
      result.push(item)
    }
  }

  return result
}

function walkLeaves(
  items: NavItem[],
  visitor: (item: NavItem, parent?: NavItem) => void,
  parent?: NavItem,
) {
  for (const item of items) {
    if (item.children?.length) {
      walkLeaves(item.children, visitor, item)
    } else if (item.path) {
      visitor(item, parent)
    }
  }
}

export function buildMenuKeyMap(items: NavItem[] = NAVIGATION_TREE): Record<string, string> {
  const map: Record<string, string> = {}
  walkLeaves(items, (item) => {
    if (item.path) {
      map[item.path] = item.key
    }
  })
  return map
}

export function buildPathMap(items: NavItem[] = NAVIGATION_TREE): Record<string, string> {
  const map: Record<string, string> = {}
  walkLeaves(items, (item) => {
    if (item.path) {
      map[item.key] = item.path
    }
  })
  return map
}

export function buildParentKeyMap(items: NavItem[] = NAVIGATION_TREE): Record<string, string> {
  const map: Record<string, string> = {}

  function walk(nodes: NavItem[], parentKey?: string) {
    for (const node of nodes) {
      if (parentKey && node.path) {
        map[node.key] = parentKey
      }
      if (node.children?.length) {
        walk(node.children, node.key)
      }
    }
  }

  walk(items)
  return map
}

export function buildBreadcrumbPathMap(items: NavItem[] = NAVIGATION_TREE): Record<string, string> {
  const map: Record<string, string> = {}

  function walk(nodes: NavItem[]) {
    for (const node of nodes) {
      if (node.path) {
        map[node.label] = node.path
      }
      if (node.children?.length) {
        const firstPath = node.children.find((c) => c.path)?.path
        if (firstPath) {
          map[node.label] = firstPath
        }
        walk(node.children)
      }
    }
  }

  walk(items)
  map['个人设置'] = '/profile'
  return map
}

export function resolveMenuKeyFromPath(
  path: string,
  menuKeyMap: Record<string, string> = buildMenuKeyMap(),
): string | undefined {
  const match = Object.keys(menuKeyMap)
    .sort((a, b) => b.length - a.length)
    .find((prefix) => path.startsWith(prefix))
  return match ? menuKeyMap[match] : undefined
}

export function buildNavCommandItems(role: NavRole | null | undefined): NavCommandItem[] {
  const filtered = filterNavByRole(NAVIGATION_TREE, role)
  const items: NavCommandItem[] = []

  function walk(nodes: NavItem[], group: string) {
    for (const node of nodes) {
      const currentGroup = node.commandGroup ?? group
      if (node.path) {
        items.push({
          key: `nav-${node.key}`,
          label: node.label,
          hint: node.path,
          group: currentGroup,
          path: node.path,
        })
      }
      if (node.children?.length) {
        walk(node.children, node.label)
      }
    }
  }

  walk(filtered, '导航')
  return items
}

export const MENU_KEY_MAP = buildMenuKeyMap()
export const NAV_PATH_MAP = buildPathMap()
export const NAV_PARENT_KEY_MAP = buildParentKeyMap()
export const BREADCRUMB_PATH_MAP = buildBreadcrumbPathMap()

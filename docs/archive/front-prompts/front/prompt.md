# FSD-Core MVP 前端设计 Prompt（API 对齐版）

## 1. 项目定位

**FSD-Core** 是商用无人配送车辆智能调度平台的管理后台。MVP 阶段验证调度主链路闭环：**订单接入 → 分配车辆 → 任务执行 → 异常处理**。

本 Prompt 面向前端工程师，所有页面设计已与后端 API 对齐，可直接进入开发。

---

## 2. 技术栈与基础规范

| 项 | 规范 |
|:---|:---|
| 框架 | Vue 3 + TypeScript |
| UI 组件库 | Ant Design Vue 4.x |
| 状态管理 | Pinia |
| HTTP 客户端 | Axios |
| 路由 | Vue Router 4 |
| 构建工具 | Vite |
| 样式 | Less + Ant Design 变量覆盖 |
| 最小分辨率 | 1366×768 |
| 主要分辨率 | 1440×900（笔记本）、1920×1080（监控室大屏） |

### 2.1 全局配置

```typescript
// API 基础配置
const API_BASE = '/api';

// 分页默认值
const DEFAULT_PAGE_SIZE = 20;
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

// 轮询间隔
const DASHBOARD_POLL_INTERVAL = 30000; // 30s

// 超时配置
const REQUEST_TIMEOUT = 10000;
```

### 2.2 统一响应处理

```typescript
interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

interface PageResponse<T> {
  total: number;
  pageNo: number;
  pageSize: number;
  records: T[];
}
```

### 2.3 时间处理

后端返回 ISO 格式字符串（如 `2026-04-28T18:00:00`），前端使用 `dayjs` 统一格式化：

```typescript
// 列表展示
dayjs(isoString).format('YYYY-MM-DD HH:mm:ss');

// 相对时间
dayjs(isoString).fromNow(); // "5分钟前"

// 日期范围筛选
// 传给后端时保持 ISO 格式或按接口要求转换
```

---

## 3. 路由设计

| 路由 | 页面 | 说明 |
|:---|:---|:---|
| `/` | 调度看板 | 默认首页，重定向 |
| `/dashboard` | 调度看板 | |
| `/orders` | 订单列表 | |
| `/orders/:orderId` | 订单详情 | |
| `/tasks` | 调度任务列表 | |
| `/tasks/:taskId` | 调度任务详情 | |
| `/vehicles` | 车辆列表 | |
| `/vehicles/:vehicleId` | 车辆详情 | |
| `/exceptions` | 异常任务列表 | |

---

## 4. 导航与布局

### 4.1 侧边栏导航

```typescript
const menuItems = [
  {
    key: 'dashboard',
    icon: 'DashboardOutlined',
    label: '调度看板',
    path: '/dashboard'
  },
  {
    key: 'orders',
    icon: 'FileTextOutlined',
    label: '订单管理',
    path: '/orders'
  },
  {
    key: 'tasks',
    icon: 'CarOutlined',
    label: '调度任务',
    path: '/tasks'
  },
  {
    key: 'vehicles',
    icon: 'ToolOutlined',
    label: '车辆管理',
    path: '/vehicles'
  },
  {
    key: 'exceptions',
    icon: 'AlertOutlined',
    label: '异常任务',
    path: '/exceptions',
    badge: 'exceptionCount' // 动态角标，绑定未处理异常数
  }
];
```

### 4.2 布局结构

```
┌─────────────────────────────────────────┐
│  Header (64px)                           │
│  Logo          Breadcrumb    UserInfo    │
├────────┬────────────────────────────────┤
│        │                                │
│ Sider  │        Content Area            │
│ (208px)│        (padding: 24px)         │
│        │                                │
│ Menu   │        Page Content            │
│        │                                │
└────────┴────────────────────────────────┘
```

### 4.3 面包屑规则

| 页面 | 面包屑 |
|:---|:---|
| 调度看板 | 首页 |
| 订单列表 | 首页 / 订单管理 |
| 订单详情 | 首页 / 订单管理 / 订单详情：{orderNo} |
| 任务列表 | 首页 / 调度任务 |
| 任务详情 | 首页 / 调度任务 / 任务详情：{taskNo} |
| 车辆列表 | 首页 / 车辆管理 |
| 车辆详情 | 首页 / 车辆管理 / 车辆详情：{vehicleCode} |
| 异常列表 | 首页 / 异常任务 |

---

## 5. 状态枚举与映射

### 5.1 订单状态

```typescript
enum OrderStatus {
  CREATED = 'CREATED',           // 已创建
  WAITING_DISPATCH = 'WAITING_DISPATCH', // 待调度
  DISPATCHED = 'DISPATCHED',     // 已调度
  IN_PROGRESS = 'IN_PROGRESS',   // 执行中
  COMPLETED = 'COMPLETED',       // 已完成
  CANCELLED = 'CANCELLED',       // 已取消
  FAILED = 'FAILED'              // 失败
}

const orderStatusMap: Record<OrderStatus, { label: string; color: string }> = {
  CREATED: { label: '已创建', color: 'default' },
  WAITING_DISPATCH: { label: '待调度', color: 'processing' },
  DISPATCHED: { label: '已调度', color: 'cyan' },
  IN_PROGRESS: { label: '执行中', color: 'warning' },
  COMPLETED: { label: '已完成', color: 'success' },
  CANCELLED: { label: '已取消', color: 'default' },
  FAILED: { label: '失败', color: 'error' }
};
```

### 5.2 调度任务状态

```typescript
enum TaskStatus {
  PENDING = 'PENDING',           // 待处理
  ASSIGNING = 'ASSIGNING',       // 派单中
  ASSIGNED = 'ASSIGNED',         // 已派单
  EXECUTING = 'EXECUTING',       // 执行中
  SUCCESS = 'SUCCESS',           // 成功
  FAILED = 'FAILED',             // 失败
  CANCELLED = 'CANCELLED',       // 已取消
  MANUAL_PENDING = 'MANUAL_PENDING' // 人工待处理
}

const taskStatusMap: Record<TaskStatus, { label: string; color: string }> = {
  PENDING: { label: '待派单', color: 'processing' },
  ASSIGNING: { label: '派单中', color: 'processing' },
  ASSIGNED: { label: '已派单', color: 'cyan' },
  EXECUTING: { label: '执行中', color: 'warning' },
  SUCCESS: { label: '已完成', color: 'success' },
  FAILED: { label: '失败', color: 'error' },
  CANCELLED: { label: '已取消', color: 'default' },
  MANUAL_PENDING: { label: '人工待处理', color: 'error' }
};
```

### 5.3 车辆状态

```typescript
enum OnlineStatus {
  ONLINE = 'ONLINE',
  OFFLINE = 'OFFLINE'
}

enum DispatchStatus {
  IDLE = 'IDLE',           // 空闲
  BUSY = 'BUSY',           // 忙碌
  UNAVAILABLE = 'UNAVAILABLE' // 不可用
}

const onlineStatusMap = {
  ONLINE: { label: '在线', color: 'success' },
  OFFLINE: { label: '离线', color: 'error' }
};

const dispatchStatusMap = {
  IDLE: { label: '空闲', color: 'success' },
  BUSY: { label: '忙碌', color: 'warning' },
  UNAVAILABLE: { label: '不可用', color: 'default' }
};
```

### 5.4 异常类型

```typescript
enum ExceptionType {
  TASK_EXECUTE_FAILED = 'TASK_EXECUTE_FAILED',
  VEHICLE_OFFLINE = 'VEHICLE_OFFLINE',
  EXECUTE_TIMEOUT = 'EXECUTE_TIMEOUT',
  STATUS_REPORT_ERROR = 'STATUS_REPORT_ERROR'
}

const exceptionTypeMap = {
  TASK_EXECUTE_FAILED: { label: '任务执行失败', icon: 'CloseCircleOutlined' },
  VEHICLE_OFFLINE: { label: '车辆离线', icon: 'DisconnectOutlined' },
  EXECUTE_TIMEOUT: { label: '执行超时', icon: 'ClockCircleOutlined' },
  STATUS_REPORT_ERROR: { label: '状态回传异常', icon: 'ExclamationCircleOutlined' }
};
```

### 5.5 异常处理状态

```typescript
enum ExceptionStatus {
  OPEN = 'OPEN',       // 待处理
  RESOLVED = 'RESOLVED' // 已处理
}

const exceptionStatusMap = {
  OPEN: { label: '待处理', color: 'error' },
  RESOLVED: { label: '已处理', color: 'success' }
};
```

---

## 6. 页面详细设计

### 6.1 调度看板（Dashboard）

**路由**：`/dashboard`  
**API**：`GET /api/admin/dashboard/summary`（30s 轮询）

#### 6.1.1 数据接口

```typescript
interface DashboardSummary {
  pendingCount: number;           // 待调度订单数
  assigningCount: number;         // 派单中
  manualPendingCount: number;     // 人工待处理
  executingCount: number;         // 执行中任务数
  failedCount: number;            // 失败数
  onlineVehicleCount: number;     // 在线车辆数
  idleVehicleCount: number;       // 空闲车辆数
  busyVehicleCount: number;       // 忙碌车辆数
}
```

#### 6.1.2 页面布局

```
[页面标题：调度看板] [刷新按钮 + 最后更新时间]

[统计卡片行 - 4列等宽]
┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│  待调度订单  │ │  执行中任务  │ │   在线车辆   │ │  未处理异常  │
│    12      │ │     8      │ │    15      │ │     3 ⚠️   │
│  ↓ 查看详情 │ │  ↓ 查看详情 │ │  ↓ 查看详情 │ │  ↓ 立即处理 │
└─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘

[下方两栏：左 2/3，右 1/3]

左栏：最近异常任务（最多5条）
┌────────────────────────────────────────┐
│ 最近异常任务                    [查看全部] │
├────────────────────────────────────────┤
│ ⚠️ 车辆离线    TSK2026...  5分钟前  [处理] │
│ ⚠️ 执行超时    TSK2026...  12分钟前 [处理] │
│ ⚠️ 任务失败    TSK2026...  30分钟前 [处理] │
│                                        │
│ 空状态：暂无未处理异常 🎉               │
└────────────────────────────────────────┘

右栏：快捷入口
┌─────────────────┐
│ 快捷操作         │
├─────────────────┤
│ [+] 创建订单     │
│ [→] 待派单任务   │
│ [!] 全部异常     │
└─────────────────┘
```

#### 6.1.3 交互细节

- **统计卡片**：点击数字跳转对应列表页并自动筛选
  - 待调度订单 → `/orders?status=WAITING_DISPATCH`
  - 执行中任务 → `/tasks?status=EXECUTING`
  - 在线车辆 → `/vehicles?onlineStatus=ONLINE`
  - 未处理异常 → `/exceptions?status=OPEN`
- **异常卡片**：`failedCount > 0` 时边框红色脉冲动画
- **轮询**：`setInterval(30000)`，组件卸载时清除
- **手动刷新**：点击刷新按钮，显示 loading，更新数据

---

### 6.2 订单管理

#### 6.2.1 订单列表页

**路由**：`/orders`  
**API**：`POST /api/admin/orders/query`

##### 请求参数

```typescript
interface OrderQueryRequest {
  orderNo?: string;           // 模糊搜索
  externalOrderNo?: string;
  status?: OrderStatus;       // 单选（后端只支持单选，前端用 Select）
  priority?: string;
  pageNo: number;
  pageSize: number;
}
```

##### 响应数据

```typescript
interface OrderAdminListItem {
  orderId: number;
  orderNo: string;
  externalOrderNo: string;
  status: OrderStatus;
  priority: string;
  dispatchTaskId: number | null;
  createdAt: string;
  updatedAt: string;
}
```

##### 页面布局

```
[页面标题：订单管理] [创建订单按钮]

[筛选区]
├─ 订单状态：Select（全部/已创建/待调度/已调度/执行中/已完成/已取消/失败）
├─ 订单编号：Input（placeholder: 支持模糊搜索）
├─ 创建时间：RangePicker
└─ [查询] [重置]

[表格]
列定义：
1. 订单编号     → orderNo（蓝色链接，点击跳转详情）
2. 状态         → status（Badge，使用 orderStatusMap）
3. 优先级       → priority
4. 关联任务     → dispatchTaskId（有值则蓝色链接，无值显示 "-"）
5. 创建时间     → createdAt（YYYY-MM-DD HH:mm:ss）
6. 更新时间     → updatedAt
7. 操作         → [查看] [取消]

操作规则：
- 查看：所有状态可见
- 取消：仅 WAITING_DISPATCH、DISPATCHED 可见
  → 点击弹出 Confirm：「确定取消订单 {orderNo}？取消后不可恢复」
  → 确认后调用取消接口（MVP 暂用模拟或预留）
```

##### 创建订单弹窗

```typescript
// 表单字段
interface CreateOrderForm {
  externalOrderNo?: string;   // 外部订单号
  bizType: string;            // 业务类型，默认 DELIVERY
  pickupPointId: number;      // 取货点
  dropoffPointId: number;     // 送货点
  priority: string;           // 优先级，默认 P2
  remark?: string;             // 备注
}

// 提交后：Message.success，关闭弹窗，刷新列表，高亮新订单行
```

#### 6.2.2 订单详情页

**路由**：`/orders/:orderId`  
**API**：`GET /api/admin/orders/{orderId}`

##### 响应数据

```typescript
interface OrderDetailResponse {
  orderId: number;
  orderNo: string;
  externalOrderNo: string;
  sourceType: string;         // MANUAL / API
  bizType: string;
  pickupPointId: number;
  dropoffPointId: number;
  priority: string;
  status: OrderStatus;
  dispatchTaskId: number | null;
  remark: string | null;
  createdAt: string;
  updatedAt: string;
}
```

##### 页面布局

```
[面包屑：首页 / 订单管理 / 订单详情：ORD202604281800001234]
[返回按钮]

[基本信息卡片]
┌────────────────────────────────────────┐
│ 订单信息                                │
├────────────────────────────────────────┤
│ 订单编号：ORD202604281800001234         │
│ 外部单号：EXT-001                       │
│ 来源类型：人工创建                       │
│ 业务类型：配送                          │
│ 优先级：P1                             │
│ 当前状态：[Badge: 已调度]               │
│ 关联任务：[TSK2026...]（链接）          │
│ 备注：test order                        │
│ 创建时间：2026-04-28 18:00:00           │
│ 更新时间：2026-04-28 18:01:00           │
└────────────────────────────────────────┘

[状态流转时间线 - MVP 用模拟数据或后端补充]
节点：
● 创建订单          2026-04-28 18:00:00    系统
○ 进入待调度池       2026-04-28 18:00:01    系统
○ 自动派单成功       2026-04-28 18:01:00    系统
○ 任务执行中         --                     --
○ 完成              --                     --

[操作日志表格 - MVP 预留]
```

---

### 6.3 调度任务管理

#### 6.3.1 任务列表页

**路由**：`/tasks`  
**API**：`POST /api/admin/tasks/query`

##### 请求参数

```typescript
interface TaskQueryRequest {
  taskNo?: string;
  orderId?: number;
  vehicleId?: number;
  status?: TaskStatus;        // 单选
  manualFlag?: boolean;       // 已收参但筛选未生效，前端可传但不强依赖
  pageNo: number;
  pageSize: number;
}
```

##### 响应数据

```typescript
interface TaskAdminListItem {
  taskId: number;
  taskNo: string;
  orderId: number;
  vehicleId: number | null;
  status: TaskStatus;
  failReasonCode: string | null;
  failReasonMsg: string | null;
  createdAt: string;
  updatedAt: string;
}
```

##### 页面布局

```
[页面标题：调度任务]

[筛选区]
├─ 任务状态：Select（全部/待派单/派单中/已派单/执行中/已完成/失败/已取消/人工待处理）
├─ 任务编号：Input
├─ 订单ID：InputNumber
├─ 车辆ID：InputNumber
└─ [查询] [重置]

[表格]
列定义：
1. 任务编号     → taskNo（蓝色链接）
2. 关联订单     → orderId（蓝色链接）
3. 车辆ID       → vehicleId（有值蓝色链接，无值 "-"）
4. 状态         → status（Badge，使用 taskStatusMap）
5. 派单类型     → dispatchType（AUTO / MANUAL）
6. 创建时间     → createdAt
7. 操作         → [查看] [派单] [改派] [取消]

操作规则：
- 查看：所有状态
- 派单：仅 PENDING、MANUAL_PENDING 可见
  → 打开派单弹窗
- 改派：仅 ASSIGNED 可见
  → 打开改派弹窗
- 取消：除 EXECUTING 外可见
  → Confirm 确认
```

##### 派单弹窗

```typescript
// GET /api/admin/vehicles/query 查询可用车辆
// 筛选条件：onlineStatus=ONLINE, dispatchStatus=IDLE

interface DispatchForm {
  vehicleId: number;    // Select，选项格式：vehicleCode / vehicleName
  remark?: string;      // 派单原因，选填
}

// 提交后：刷新任务列表，Message.success
```

##### 改派弹窗

```typescript
interface ReassignForm {
  currentVehicleId: number;   // 禁用展示
  newVehicleId: number;       // Select，筛选在线空闲车辆，排除当前车辆
  reason: string;             // 改派原因，必填，min: 5
}

// 顶部 Alert：「改派将解除原车辆绑定，请确认原车辆已停止执行任务」
// 提交后：刷新列表，Message.success("改派成功")
```

#### 6.3.2 任务详情页

**路由**：`/tasks/:taskId`  
**API**：`GET /api/admin/tasks/{taskId}`

##### 响应数据

```typescript
interface TaskDetailResponse {
  taskId: number;
  taskNo: string;
  orderId: number;
  vehicleId: number | null;
  dispatchType: string;       // AUTO / MANUAL
  status: TaskStatus;
  failReasonCode: string | null;
  failReasonMsg: string | null;
  assignTime: string | null;
  startTime: string | null;
  finishTime: string | null;
  manualFlag: number;
  retryCount: number;
  remark: string | null;
}
```

##### 页面布局

```
[面包屑：首页 / 调度任务 / 任务详情：TSK202604281801001234]

[三列信息卡片区]
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│  任务信息    │ │   车辆信息   │ │   操作区     │
│             │ │             │ │             │
│ 编号：TSK... │ │ 车辆：VH-001│ │ [派单]      │
│ 状态：[Badge]│ │ 状态：在线   │ │ [改派]      │
│ 类型：自动   │ │ 调度：忙碌   │ │ [取消]      │
│ 订单：[链接] │ │ 任务：[链接] │ │             │
│ 重试：0次   │ │ 电量：86%   │ │             │
└─────────────┘ └─────────────┘ └─────────────┘

[任务状态时间线]
节点：
● 创建任务      2026-04-28 18:01:00    系统
○ 自动派单      2026-04-28 18:01:30    系统
○ 车辆接单      --                     --
○ 开始执行      --                     --
○ 完成          --                     --

[改派记录 - 有数据时展示]
[车辆状态回传日志 - MVP 预留，展示模拟数据]
```

---

### 6.4 异常任务处理

#### 6.4.1 异常列表页

**路由**：`/exceptions`  
**API**：`POST /api/admin/exceptions/query`

##### 请求参数

```typescript
interface ExceptionQueryRequest {
  exceptionType?: ExceptionType;
  exceptionStatus?: ExceptionStatus;  // OPEN / RESOLVED
  taskNo?: string;                    // 已收参但筛选未生效
  orderId?: number;
  vehicleId?: number;
  pageNo: number;
  pageSize: number;
}
```

##### 响应数据

```typescript
interface ExceptionAdminListItem {
  id: number;
  taskId: number;
  orderId: number;
  vehicleId: number | null;
  exceptionType: ExceptionType;
  exceptionStatus: ExceptionStatus;
  exceptionMsg: string;
  occurTime: string;
  resolvedTime: string | null;
  resolverId: string | null;
  resolveRemark: string | null;
  createdAt: string;
  updatedAt: string;
}
```

##### 页面布局

```
[页面标题：异常任务] [未处理异常角标同步到导航]

[筛选区]
├─ 异常类型：Select（全部/任务执行失败/车辆离线/执行超时/状态回传异常）
├─ 处理状态：Select（全部/待处理/已处理）
├─ 订单ID：InputNumber
├─ 车辆ID：InputNumber
└─ [查询] [重置]

[表格]
列定义：
1. 异常ID       → id
2. 异常类型     → exceptionType（图标 + 文字，使用 exceptionTypeMap）
3. 关联任务     → taskId（蓝色链接）
4. 异常信息     → exceptionMsg（tooltip 展示完整内容）
5. 发生时间     → occurTime
6. 处理状态     → exceptionStatus（Badge）
7. 处理人       → resolverId（待处理显示 "-"）
8. 操作         → [处理] [查看任务]

操作规则：
- 处理：仅 OPEN 状态可见 → 打开处理抽屉
- 查看任务：所有状态可见 → 跳转任务详情
```

#### 6.4.2 异常处理抽屉

**组件**：Drawer，宽度 600px，右侧滑出  
**API**：`POST /api/admin/exceptions/{exceptionId}/resolve`

##### 请求参数

```typescript
interface ResolveExceptionRequest {
  resolverId: string;       // 当前用户ID
  resolverName: string;       // 当前用户名
  action: string;             // MARK_FAILED / RETRY / IGNORE / CONTACT
  remark: string;             // 处理说明，必填
}
```

##### 页面布局

```
[抽屉标题：处理异常 #E20240428001]

[异常信息 - 只读]
┌────────────────────────────────────────┐
│ 异常类型：[图标] 车辆离线                 │
│ 关联任务：[TSK2026...]                   │
│ 发生时间：2026-04-28 18:10:00           │
│ 异常详情：车辆 VH-001 超过5分钟未上报状态  │
└────────────────────────────────────────┘

[处理表单]
├─ 处理结果 *：RadioGroup
│   ○ 重新派单（选择后需额外选择车辆）
│   ○ 忽略异常
│   ○ 联系现场
│   ○ 标记失败
├─ 处理说明 *：TextArea，min: 10
└─ [取消] [提交处理]

提交后：
- Drawer 关闭
- Message.success
- 刷新异常列表
- 导航 Badge 数字减 1（若该异常为最后一个待处理）
```

---

### 6.5 车辆管理

#### 6.5.1 车辆列表页

**路由**：`/vehicles`  
**API**：`POST /api/admin/vehicles/query`

##### 请求参数

```typescript
interface VehicleQueryRequest {
  vehicleCode?: string;       // 模糊搜索
  onlineStatus?: OnlineStatus; // ONLINE / OFFLINE
  dispatchStatus?: DispatchStatus; // IDLE / BUSY / UNAVAILABLE
  pageNo: number;
  pageSize: number;
}
```

##### 响应数据

```typescript
interface VehicleAdminListItem {
  vehicleId: number;
  vehicleCode: string;        // 车牌/编号
  vehicleName: string;
  onlineStatus: OnlineStatus;
  dispatchStatus: DispatchStatus;
  currentTaskId: number | null;
  currentOrderId: number | null;
  batteryLevel: number;         // 电量百分比
  lastReportTime: string;
}
```

##### 页面布局

```
[页面标题：车辆管理]

[筛选区]
├─ 在线状态：Select（全部/在线/离线）
├─ 调度状态：Select（全部/空闲/忙碌/不可用）
├─ 车辆编号：Input
└─ [查询] [重置]

[表格]
列定义：
1. 车辆编号     → vehicleCode（蓝色链接）
2. 车辆名称     → vehicleName
3. 在线状态     → onlineStatus（Badge）
4. 调度状态     → dispatchStatus（Badge）
5. 当前任务     → currentTaskId（有值蓝色链接，无值 "-"）
6. 电量         → batteryLevel（Progress 组件，低于 20% 红色）
7. 最后回传     → lastReportTime（相对时间 + tooltip 精确时间）
8. 操作         → [查看]

特殊样式：
- onlineStatus=OFFLINE 且 lastReportTime > 5分钟前：整行背景 #FFF2F0
```

#### 6.5.2 车辆详情页

**路由**：`/vehicles/:vehicleId`  
**API**：`GET /api/admin/vehicles/{vehicleId}`

##### 响应数据

```typescript
interface VehicleDetailResponse {
  vehicleId: number;
  vehicleCode: string;
  vehicleName: string;
  vehicleType: string;        // CAR / TRUCK 等
  onlineStatus: OnlineStatus;
  dispatchStatus: DispatchStatus;
  currentTaskId: number | null;
  currentOrderId: number | null;
  currentLatitude: number;
  currentLongitude: number;
  batteryLevel: number;
  lastReportTime: string;
  remark: string | null;
}
```

##### 页面布局

```
[面包屑：首页 / 车辆管理 / 车辆详情：VH-001]

[两列信息卡片区]
┌────────────────────────┐ ┌────────────────────────┐
│ 基本信息                │ │ 实时状态                │
│ 编号：VH-001           │ │ 在线状态：[Badge]       │
│ 名称：Vehicle 1        │ │ 调度状态：[Badge]       │
│ 类型：CAR              │ │ 电量：86%              │
│                        │ │ 最后回传：5分钟前       │
│                        │ │ 当前位置：31.23, 121.47 │
│                        │ │ 当前任务：[TSK...]      │
└────────────────────────┘ └────────────────────────┘

[在线状态变更历史 - MVP 预留/模拟]
[状态回传日志 - MVP 预留/模拟，时间倒序]
```

---

## 7. 全局组件规范

### 7.1 表格通用配置

```typescript
const tableConfig = {
  pagination: {
    showSizeChanger: true,
    showQuickJumper: true,
    showTotal: (total: number) => `共 ${total} 条`,
    pageSizeOptions: [10, 20, 50, 100],
    defaultPageSize: 20
  },
  scroll: { x: 'max-content' },  // 小屏横向滚动
  rowKey: 'id',                  // 每页唯一 key
};
```

### 7.2 操作确认弹窗

```typescript
// 取消订单
Modal.confirm({
  title: '确认取消订单？',
  content: `订单编号：${orderNo}，取消后不可恢复。`,
  okText: '确认取消',
  okType: 'danger',
  cancelText: '再想想',
  onOk: () => cancelOrder(orderId)
});

// 取消任务
Modal.confirm({
  title: '确认取消任务？',
  content: `任务编号：${taskNo}，执行中任务不可取消。`,
  okText: '确认取消',
  okType: 'danger',
  cancelText: '再想想'
});
```

### 7.3 消息反馈

```typescript
// 成功
message.success(`操作成功：${sourceStatus} → ${targetStatus}`);

// 错误
message.error(error.message || '操作失败，请重试');

// 网络异常
message.error('网络异常，请检查网络后重试');
```

### 7.4 空状态

```vue
<Empty 
  description="暂无数据" 
  :image="Empty.PRESENTED_IMAGE_SIMPLE"
>
  <a-button type="primary" @click="openCreateModal">创建订单</a-button>
</Empty>
```

---

## 8. 错误处理与容错

| 场景 | 处理策略 |
|:---|:---|
| 401 未登录 | 跳转登录页（MVP 预留，当前后端无鉴权） |
| 403 无权限 | 显示 403 页面 |
| 404 数据不存在 | 显示「数据不存在或已被删除」，提供返回列表按钮 |
| 409 并发冲突 | Message.error("数据已被修改，请刷新后重试")，自动刷新列表 |
| 422 校验失败 | 表单高亮错误字段，展示后端返回的错误信息 |
| 500 服务端错误 | Message.error("服务器繁忙，请稍后重试") |
| 网络超时 | 请求 10s 无响应，自动取消并提示 |
| 车辆状态变更（并发） | 提交时若车辆已被占用，返回错误，刷新车辆下拉列表 |

---

## 9. 开发优先级

按以下顺序开发，每完成一个页面可独立联调：

| 优先级 | 页面 | 依赖 API |
|:---|:---|:---|
| P0 | 调度看板 | `GET /api/admin/dashboard/summary` |
| P0 | 调度任务列表 | `POST /api/admin/tasks/query` |
| P0 | 调度任务详情 | `GET /api/admin/tasks/{taskId}` |
| P0 | 异常列表 | `POST /api/admin/exceptions/query` |
| P0 | 异常处理抽屉 | `POST /api/admin/exceptions/{id}/resolve` |
| P1 | 订单列表 | `POST /api/admin/orders/query` |
| P1 | 订单详情 | `GET /api/admin/orders/{orderId}` |
| P1 | 车辆列表 | `POST /api/admin/vehicles/query` |
| P1 | 车辆详情 | `GET /api/admin/vehicles/{vehicleId}` |

---

## 10. 交付验收标准

- [ ] 所有页面路由可正常访问，面包屑正确
- [ ] 所有列表页支持分页、筛选、排序（时间默认倒序）
- [ ] 状态 Badge 颜色与枚举映射表完全一致
- [ ] 所有 ID 字段可点击跳转关联详情页
- [ ] 派单/改派/异常处理等操作有完整表单校验和二次确认
- [ ] 看板数据 30s 自动刷新，手动刷新可用
- [ ] 异常导航角标实时同步未处理数量
- [ ] 空状态有引导操作
- [ ] 1366×768 分辨率下无布局错乱
- [ ] 所有 API 调用使用统一封装，响应错误统一处理

---

**请基于以上规范，使用 Vue 3 + Ant Design Vue 实现 FSD-Core MVP 管理后台。**
# FSD-Core 前端开发任务流程

> 基于 `prompt.md` 项目定位、`frontend-api-handoff.md` 后端接口、`project/` 参考设计生成。
> 技术栈：Vue 3 + TypeScript + Ant Design Vue 4.x + Pinia + Axios + Vue Router 4 + Vite

---

## 阶段一：工程初始化与基础架构

- [x] 1.1 使用 Vite 创建 Vue 3 + TypeScript 项目（`fsd-web`），配置路径别名 `@/` → `src/`
- [x] 1.2 安装核心依赖：`ant-design-vue@4`、`@ant-design/icons-vue`、`pinia`、`vue-router@4`、`axios`、`dayjs`、`less`
- [x] 1.3 配置 Vite：Less 变量覆盖 Ant Design 主题色、开发代理 `/api` → 后端地址
- [x] 1.4 配置 TypeScript `tsconfig.json`：路径别名、严格模式、组件类型声明
- [x] 1.5 初始化 Git，配置 `.gitignore`、`.editorconfig`

---

## 阶段二：全局基础设施

### 2.1 API 层封装

- [x] 2.1.1 创建 `src/utils/request.ts`：Axios 实例，baseURL `/api`，timeout 10s
- [x] 2.1.2 实现响应拦截器：统一解包 `ApiResponse<T>`，错误码处理（401/403/404/409/422/500/网络超时）
- [x] 2.1.3 创建 `src/api/` 目录，按模块拆分：
  - `order.ts` — 订单相关接口
  - `task.ts` — 调度任务相关接口
  - `exception.ts` — 异常相关接口
  - `vehicle.ts` — 车辆相关接口
  - `dashboard.ts` — 看板摘要接口

### 2.2 类型定义

- [x] 2.2.1 创建 `src/types/api.d.ts`：`ApiResponse<T>`、`PageResponse<T>` 通用类型
- [x] 2.2.2 创建 `src/types/order.d.ts`：`OrderQueryRequest`、`OrderAdminListItem`、`OrderDetailResponse`、`CreateOrderForm`
- [x] 2.2.3 创建 `src/types/task.d.ts`：`TaskQueryRequest`、`TaskAdminListItem`、`TaskDetailResponse`、`DispatchForm`、`ReassignForm`
- [x] 2.2.4 创建 `src/types/exception.d.ts`：`ExceptionQueryRequest`、`ExceptionAdminListItem`、`ResolveExceptionRequest`
- [x] 2.2.5 创建 `src/types/vehicle.d.ts`：`VehicleQueryRequest`、`VehicleAdminListItem`、`VehicleDetailResponse`
- [x] 2.2.6 创建 `src/types/dashboard.d.ts`：`DashboardSummary`

### 2.3 状态枚举与映射

- [x] 2.3.1 创建 `src/constants/enums.ts`：订单状态、任务状态、车辆在线状态、车辆调度状态、异常类型、异常处理状态枚举
- [x] 2.3.2 创建 `src/constants/statusMap.ts`：各枚举的中文 label + Badge color 映射表（与 prompt.md §5 完全一致）

### 2.4 路由配置

- [x] 2.4.1 创建 `src/router/index.ts`：注册全部路由（参照 prompt.md §3）
  - `/` → 重定向到 `/dashboard`
  - `/dashboard` → 调度看板
  - `/orders` → 订单列表
  - `/orders/:orderId` → 订单详情
  - `/tasks` → 调度任务列表
  - `/tasks/:taskId` → 调度任务详情
  - `/vehicles` → 车辆列表
  - `/vehicles/:vehicleId` → 车辆详情
  - `/exceptions` → 异常列表
- [x] 2.4.2 配置路由 meta：面包屑标题、页面标题

### 2.5 全局布局

- [x] 2.5.1 创建 `src/layouts/BasicLayout.vue`：Ant Design `a-layout` + `a-layout-sider` + `a-layout-header` + `a-layout-content`
  - 侧边栏 208px，菜单项与 prompt.md §4.1 一致
  - Header 64px，含面包屑 + 用户信息
  - Content 区域 padding 24px
- [x] 2.5.2 实现侧边栏菜单：`DashboardOutlined` / `FileTextOutlined` / `CarOutlined` / `ToolOutlined` / `AlertOutlined`
- [x] 2.5.3 实现面包屑组件：根据路由 meta 自动生成（参照 prompt.md §4.3）
- [x] 2.5.4 异常菜单项动态角标：绑定未处理异常数，30s 轮询更新

### 2.6 全局配置

- [x] 2.6.1 创建 `src/config/index.ts`：`API_BASE`、`DEFAULT_PAGE_SIZE=20`、`PAGE_SIZE_OPTIONS`、`DASHBOARD_POLL_INTERVAL=30000`、`REQUEST_TIMEOUT=10000`
- [x] 2.6.2 在 `main.ts` 注册 Ant Design Vue（全量或按需引入）、Pinia、Router

---

## 阶段三：通用组件

- [x] 3.1 封装 `src/components/common/PageContainer.vue`：统一页面标题 + 内容区 slot
- [x] 3.2 封装 `src/components/common/StatusBadge.vue`：根据 status + statusMap 自动渲染 Badge
- [ ] 3.3 封装 `src/components/common/SearchForm.vue`：筛选区表单 + 查询/重置按钮通用布局
- [ ] 3.4 封装 `src/components/common/DataTable.vue`：统一表格配置（分页 showSizeChanger、showQuickJumper、showTotal、scroll.x）
- [ ] 3.5 封装 `src/components/common/DetailCard.vue`：详情页信息卡片（label-value 网格布局）
- [x] 3.6 封装 `src/components/common/EmptyState.vue`：空状态 + 引导操作按钮

---

## 阶段四：P0 页面开发

### 4.1 调度看板

- [x] 4.1.1 创建 `src/views/dashboard/Index.vue`：页面骨架
- [x] 4.1.2 实现 4 列统计卡片：待调度订单 / 执行中任务 / 在线车辆 / 未处理异常
  - 数据来源 `GET /api/admin/dashboard/summary`
  - 点击数字跳转对应列表页并自动筛选
  - `failedCount > 0` 时异常卡片边框红色脉冲动画
- [x] 4.1.3 实现下方左栏：最近异常任务列表（最多 5 条）+ [查看全部] 跳转
- [x] 4.1.4 实现下方右栏：快捷入口（创建订单 / 待派单任务 / 全部异常）
- [x] 4.1.5 实现 30s 自动轮询 + 手动刷新按钮 + 最后更新时间显示
- [x] 4.1.6 创建 Pinia store `src/stores/dashboard.ts`：管理看板数据与轮询逻辑

### 4.2 调度任务列表

- [x] 4.2.1 创建 `src/views/task/List.vue`：页面骨架
- [x] 4.2.2 实现筛选区：任务状态 Select / 任务编号 Input / 订单 ID InputNumber / 车辆 ID InputNumber + 查询/重置
- [x] 4.2.3 实现表格列：任务编号(链接) / 关联订单(链接) / 车辆 ID(链接) / 状态 Badge / 派单类型 / 创建时间 / 操作
- [x] 4.2.4 实现操作按钮：查看(所有) / 派单(PENDING, MANUAL_PENDING) / 改派(ASSIGNED) / 取消(除 EXECUTING)
- [x] 4.2.5 实现派单弹窗 Modal：车辆 Select（在线空闲车辆）+ 备注 Input + 提交
- [x] 4.2.6 实现改派弹窗 Modal：当前车辆(只读) + 新车辆 Select（排除当前）+ 改派原因(必填 min 5) + 顶部 Alert 警告
- [x] 4.2.7 实现取消确认 Modal
- [x] 4.2.8 对接 `POST /api/admin/tasks/query`，创建 Pinia store `src/stores/task.ts`
- [x] 4.2.9 支持 URL query 参数同步筛选状态（从看板跳转时自动填充）

### 4.3 调度任务详情

- [x] 4.3.1 创建 `src/views/task/Detail.vue`：页面骨架 + 面包屑
- [x] 4.3.2 实现三列信息卡片：任务信息 / 车辆信息 / 操作区（派单/改派/取消按钮，按状态显示）
- [x] 4.3.3 实现任务状态时间线 Timeline 组件
- [x] 4.3.4 对接 `GET /api/admin/tasks/{taskId}`，车辆信息需额外请求 `GET /api/admin/vehicles/{vehicleId}`
- [x] 4.3.5 操作区按钮复用列表页的派单/改派/取消弹窗逻辑

### 4.4 异常列表

- [x] 4.4.1 创建 `src/views/exception/Index.vue`：页面骨架
- [x] 4.4.2 实现筛选区：异常类型 Select / 处理状态 Select / 订单 ID InputNumber / 车辆 ID InputNumber + 查询/重置
- [x] 4.4.3 实现表格列：异常 ID / 异常类型(图标+文字) / 关联任务(链接) / 异常信息(tooltip) / 发生时间 / 处理状态 Badge / 处理人 / 操作
- [x] 4.4.4 实现操作按钮：处理(仅 OPEN) / 查看任务(所有，跳转任务详情)
- [x] 4.4.5 对接 `POST /api/admin/exceptions/query`，创建 Pinia store `src/stores/exception.ts`
- [x] 4.4.6 支持 URL query 参数同步（从看板跳转时自动筛选）

### 4.5 异常处理抽屉

- [x] 4.5.1 实现 Drawer 600px 右侧滑出（集成在异常列表页内）
- [x] 4.5.2 实现只读异常信息展示区：异常类型(图标) / 关联任务 / 发生时间 / 异常详情
- [x] 4.5.3 实现处理表单：处理结果 RadioGroup（重新派单/忽略异常/联系现场/标记失败）+ 处理说明 TextArea(必填 min 10)
- [x] 4.5.4 「重新派单」选项选中时展示额外车辆选择 Select
- [x] 4.5.5 对接 `POST /api/admin/exceptions/{exceptionId}/resolve`
- [x] 4.5.6 提交成功后：关闭 Drawer + Message.success + 刷新列表 + 更新导航角标

---

## 阶段五：P1 页面开发

### 5.1 订单列表

- [x] 5.1.1 创建 `src/views/order/List.vue`：页面骨架
- [x] 5.1.2 实现筛选区：订单状态 Select / 订单编号 Input + 查询/重置
- [x] 5.1.3 实现表格列：订单编号(链接) / 状态 Badge / 优先级 / 关联任务(链接) / 创建时间 / 更新时间 / 操作
- [x] 5.1.4 实现操作按钮：查看(所有) / 取消(仅 WAITING_DISPATCH, DISPATCHED) + 取消确认 Modal
- [x] 5.1.5 对接 `POST /api/admin/orders/query`，创建 Pinia store `src/stores/order.ts`
- [x] 5.1.6 支持 URL query 参数同步（从看板跳转时自动筛选）

### 5.2 订单详情

- [x] 5.2.1 创建 `src/views/order/Detail.vue`：页面骨架 + 面包屑
- [x] 5.2.2 实现基本信息卡片：订单编号 / 外部单号 / 来源类型 / 业务类型 / 优先级 / 当前状态 Badge / 关联任务(链接) / 备注 / 创建时间 / 更新时间
- [x] 5.2.3 实现状态流转时间线 Timeline 组件（MVP 可用模拟数据）
- [x] 5.2.4 预留操作日志表格区域
- [x] 5.2.5 对接 `GET /api/admin/orders/{orderId}`

### 5.3 车辆列表

- [x] 5.3.1 创建 `src/views/vehicle/List.vue`：页面骨架
- [x] 5.3.2 实现筛选区：在线状态 Select / 调度状态 Select / 车辆编号 Input + 查询/重置
- [x] 5.3.3 实现表格列：车辆编号(链接) / 车辆名称 / 在线状态 Badge / 调度状态 Badge / 当前任务(链接) / 电量 Progress(低于20%红色) / 最后回传(相对时间+tooltip精确时间) / 操作
- [x] 5.3.4 离线超 5 分钟行特殊背景样式 `#FFF2F0`
- [x] 5.3.5 对接 `POST /api/admin/vehicles/query`，创建 Pinia store `src/stores/vehicle.ts`

### 5.4 车辆详情

- [x] 5.4.1 创建 `src/views/vehicle/Detail.vue`：页面骨架 + 面包屑
- [x] 5.4.2 实现两列信息卡片：基本信息（编号/名称/类型）+ 实时状态（在线/调度/电量/最后回传/当前位置/当前任务）
- [x] 5.4.3 预留在线状态变更历史 / 状态回传日志区域（MVP 用模拟数据）
- [x] 5.4.4 对接 `GET /api/admin/vehicles/{vehicleId}`

---

## 阶段六：全局交互与体验优化

- [x] 6.1 全局错误处理中间件：401 跳转登录页（预留）/ 403 展示 403 页面 / 404 展示 404 页面
- [x] 6.2 表单校验统一：422 错误高亮字段 + 展示后端错误信息
- [x] 6.3 409 并发冲突处理：Message.error + 自动刷新列表
- [ ] 6.4 列表页时间字段默认倒序排列
- [x] 6.5 所有 ID 字段可点击跳转关联详情页（全局 linkable 封装）
- [x] 6.6 Loading 状态统一处理：页面级 Skeleton / 按钮级 loading
- [x] 6.7 空状态引导：列表为空时展示引导操作按钮

---

## 阶段七：响应式与兼容性

- [ ] 7.1 最小分辨率 1366×768 布局适配验证
- [ ] 7.2 主要分辨率 1440×900 / 1920×1080 显示验证
- [x] 7.3 表格小屏横向滚动 `scroll: { x: 'max-content' }`
- [x] 7.4 侧边栏折叠适配（可选）

---

## 阶段八：联调与验收

- [ ] 8.1 接入真实后端 API，逐页面联调
- [ ] 8.2 看板 30s 轮询 + 异常角标同步验证
- [ ] 8.3 派单/改派/异常处理等操作完整流程验证
- [ ] 8.4 分页、筛选、重置功能验证
- [ ] 8.5 所有状态 Badge 颜色与枚举映射表一致性检查
- [ ] 8.6 面包屑导航与路由跳转完整性检查
- [ ] 8.7 按 prompt.md §10 交付验收标准逐项通过

---

## 文件结构参考

```
front/
├── public/
├── src/
│   ├── api/                    # API 接口层
│   │   ├── order.ts
│   │   ├── task.ts
│   │   ├── exception.ts
│   │   ├── vehicle.ts
│   │   └── dashboard.ts
│   ├── components/
│   │   └── common/             # 通用组件
│   │       ├── PageContainer.vue
│   │       ├── StatusBadge.vue
│   │       └── EmptyState.vue
│   ├── config/
│   │   └── index.ts
│   ├── constants/
│   │   ├── enums.ts
│   │   └── statusMap.ts
│   ├── layouts/
│   │   └── BasicLayout.vue
│   ├── router/
│   │   └── index.ts
│   ├── stores/                 # Pinia 状态管理
│   │   ├── dashboard.ts
│   │   ├── order.ts
│   │   ├── task.ts
│   │   ├── exception.ts
│   │   └── vehicle.ts
│   ├── styles/
│   │   ├── theme.less          # Ant Design 主题变量
│   │   └── global.less         # 全局样式 + 暗色主题
│   ├── types/                  # TypeScript 类型
│   │   ├── api.d.ts
│   │   ├── order.d.ts
│   │   ├── task.d.ts
│   │   ├── exception.d.ts
│   │   ├── vehicle.d.ts
│   │   └── dashboard.d.ts
│   ├── utils/
│   │   └── request.ts
│   ├── views/
│   │   ├── dashboard/
│   │   │   └── Index.vue
│   │   ├── order/
│   │   │   ├── List.vue
│   │   │   └── Detail.vue
│   │   ├── task/
│   │   │   ├── List.vue
│   │   │   └── Detail.vue
│   │   ├── exception/
│   │   │   └── Index.vue
│   │   └── vehicle/
│   │       ├── List.vue
│   │       └── Detail.vue
│   ├── App.vue
│   └── main.ts
├── index.html
├── vite.config.ts
├── tsconfig.json
├── tsconfig.node.json
├── env.d.ts
├── package.json
└── .gitignore
```

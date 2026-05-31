# FSD-Core MVP 设计文档

## 1. 文档目标

本文档用于定义 `FSD-Core MVP` 的技术选型、系统边界、模块划分、数据模型、状态机、中间件使用原则和工程约束，作为开发设计基线。

## 2. 技术选型

### 2.1 后端

- `Java 21` 作为项目目标版本
- `Spring Boot 3.x`
- `Spring Cloud Alibaba` 按需引入
- `MyBatis-Plus`
- `MySQL 8`
- `Redis 7`
- `RabbitMQ 3.x`
- `Spring Validation`
- `MapStruct`
- `Springdoc OpenAPI`

说明：

- 本机可使用 `Java 25`，但项目编译目标固定为 `Java 21`
- MVP 阶段优先稳定性和兼容性

### 2.2 前端

- `Vue 3`
- `TypeScript`
- `Vite`
- `Pinia`
- `Vue Router`
- `Element Plus`
- `Axios`

### 2.3 工程化

- `Maven`
- `Docker`
- `Nginx`
- `Git`
- `JUnit 5 + Mockito`

## 3. 架构形态

MVP 阶段采用 `模块化单体`。

原因：

- 订单、调度、车辆之间业务耦合较强
- MVP 阶段需求变动频繁
- 过早拆分微服务会显著增加联调和一致性成本

## 4. 模块划分

后端模块建议：

- `fsd-common`
- `fsd-order`
- `fsd-dispatch`
- `fsd-vehicle`
- `fsd-admin-api`
- `fsd-bootstrap`

### 4.1 fsd-common

职责：

- 通用异常
- 公共枚举
- 基础工具
- 日志上下文
- 公共配置

### 4.2 fsd-order

职责：

- 订单创建
- 订单查询
- 订单取消
- 订单状态维护

### 4.3 fsd-dispatch

职责：

- 调度任务生成
- 自动派单
- 手动派单
- 改派
- 调度任务状态流转
- 调度异常处理入口

### 4.4 fsd-vehicle

职责：

- 车辆基础信息
- 在线状态
- 可调度状态
- 车辆状态回传处理

### 4.5 fsd-admin-api

职责：

- 后台管理接口聚合
- 调度监控
- 异常处理入口

### 4.6 fsd-bootstrap

职责：

- 应用启动
- 组件装配
- 基础配置加载

## 5. 模块依赖关系

推荐依赖方向：

- `fsd-bootstrap -> all`
- `fsd-admin-api -> fsd-order / fsd-dispatch / fsd-vehicle / fsd-common`
- `fsd-dispatch -> fsd-order / fsd-vehicle / fsd-common`
- `fsd-order -> fsd-common`
- `fsd-vehicle -> fsd-common`

约束：

- 禁止跨模块直接操作对方 Mapper
- 跨模块调用走服务层
- 不允许将核心业务逻辑放入 Controller

## 6. 核心数据模型

### 6.1 t_order

关键字段：

- `id`
- `order_no`
- `external_order_no`
- `source_type`
- `biz_type`
- `pickup_point_id`
- `dropoff_point_id`
- `priority`
- `status`
- `dispatch_task_id`
- `created_at`
- `updated_at`
- `version`
- `deleted`

### 6.2 t_dispatch_task

关键字段：

- `id`
- `task_no`
- `order_id`
- `vehicle_id`
- `dispatch_type`
- `status`
- `fail_reason_code`
- `fail_reason_msg`
- `assign_time`
- `start_time`
- `finish_time`
- `manual_flag`
- `retry_count`
- `version`

### 6.3 t_vehicle

关键字段：

- `id`
- `vehicle_code`
- `vehicle_name`
- `online_status`
- `dispatch_status`
- `current_task_id`
- `current_order_id`
- `current_latitude`
- `current_longitude`
- `battery_level`
- `last_report_time`
- `version`

### 6.4 t_dispatch_task_operate_log

关键字段：

- `task_id`
- `operate_type`
- `before_status`
- `after_status`
- `operator_type`
- `operator_id`
- `operate_remark`
- `created_at`

### 6.5 t_dispatch_exception_record

关键字段：

- `task_id`
- `order_id`
- `vehicle_id`
- `exception_type`
- `exception_status`
- `exception_msg`
- `occur_time`
- `resolved_time`

## 7. 状态机设计

### 7.1 订单状态

- `CREATED`
- `WAITING_DISPATCH`
- `DISPATCHED`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`
- `FAILED`

主流程：

- `CREATED -> WAITING_DISPATCH -> DISPATCHED -> IN_PROGRESS -> COMPLETED`

### 7.2 调度任务状态

- `PENDING`
- `ASSIGNING`
- `ASSIGNED`
- `EXECUTING`
- `SUCCESS`
- `FAILED`
- `CANCELLED`
- `MANUAL_PENDING`

主流程：

- `PENDING -> ASSIGNING -> ASSIGNED -> EXECUTING -> SUCCESS`

### 7.3 车辆状态

在线状态：

- `ONLINE`
- `OFFLINE`

调度状态：

- `IDLE`
- `BUSY`
- `UNAVAILABLE`

## 8. 核心业务流

### 8.1 订单创建流

1. 创建订单
2. 订单落库
3. 订单进入待调度池
4. 触发调度入口

### 8.2 派单流

1. 调度模块接收待调度订单
2. 获取车辆候选集
3. 校验车辆可用性
4. 创建或更新调度任务
5. 更新订单和车辆关联状态

### 8.3 车辆状态回传流

1. 车辆上报状态
2. 更新车辆最新状态
3. 若涉及任务推进，同步更新调度任务状态
4. 必要时推进订单状态

### 8.4 异常处理流

1. 识别异常
2. 记录异常表
3. 标记任务待人工处理
4. 调度员执行改派或取消

## 9. Redis 使用设计

使用范围：

- 车辆在线状态缓存
- 候选车辆短时缓存
- 幂等键
- 调度短锁

建议 Key：

- `fsd:vehicle:online:{vehicleCode}`
- `fsd:dispatch:candidate:{orderId}`
- `fsd:idempotent:state-report:{vehicleCode}:{reportTime}`
- `fsd:lock:dispatch:task:{taskId}`

设计约束：

- Redis 不是最终真相源
- 分布式锁只做辅助保护，不替代数据库状态校验

## 10. RabbitMQ 使用设计

建议事件：

- `order.waiting.dispatch`
- `dispatch.task.assigned`
- `dispatch.task.failed`
- `vehicle.state.reported`
- `dispatch.exception.occurred`

事件体必须包含：

- `eventId`
- `eventTime`
- 业务主键

设计约束：

- 消费端必须幂等
- 重试、死信、补偿路径必须显式设计

## 11. 接口设计原则

- Controller 只负责参数接收、校验和响应封装
- Service 负责业务逻辑
- Repository 负责持久化
- DTO、VO、Entity 分离
- 不将 Entity 直接暴露给前端

## 12. 工程约束

- 强制分层：`Controller -> Service -> Repository`
- 命名必须表达业务语义
- 异常处理必须可追踪
- 复杂逻辑必须补中文注释说明原因和约束
- 核心状态流转必须编写测试

## 13. 第一阶段依赖清单

后端核心依赖：

- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `spring-boot-starter-aop`
- `spring-boot-starter-actuator`
- `mybatis-plus-spring-boot3-starter`
- `mysql-connector-j`
- `spring-boot-starter-data-redis`
- `spring-boot-starter-amqp`
- `springdoc-openapi-starter-webmvc-ui`
- `mapstruct`
- `lombok`

测试依赖：

- `spring-boot-starter-test`
- `mockito-junit-jupiter`
- `testcontainers`

## 14. 设计结论

`FSD-Core MVP` 的设计重点不是做成完整商业平台，而是交付一套边界清晰、状态明确、可追踪、可人工干预的最小调度系统。

后续扩展必须以本设计文档定义的模块边界、状态机和中间件约束为基础。

# FSD-Core MVP 接口定义清单（OpenAPI 风格）

## 1. 文档说明

- 文档目标：定义 `FSD-Core MVP` 的接口契约草案，供后端开发、前端联调、测试设计使用。
- 文档范围：仅覆盖 `订单接入 -> 调度分配 -> 车辆执行 -> 异常处理 -> 人工干预` 的 MVP 闭环。
- 设计约束：
  - 最终业务真相源为 `MySQL`
  - `Redis` 仅用于缓存、幂等和短锁
  - `RabbitMQ` 仅用于异步事件传播
  - 所有关键状态迁移必须经由服务层校验

## 2. 基础约定

### 2.1 服务前缀

- Base URL：`/api`

### 2.2 通用响应结构

说明：沿用项目既有统一响应模型，不在此文档中新造全局响应包裹结构。

### 2.3 通用字段约定

- 分页请求字段：
  - `pageNo`
  - `pageSize`
- 时间字段格式：
  - `yyyy-MM-dd HH:mm:ss`
- 主键类型：
  - `Long`
- 状态字段：
  - 使用字符串枚举，不直接暴露数值魔法值

## 3. 标签分组

- `Order`：订单接口
- `Dispatch`：调度任务接口
- `Vehicle`：车辆接口
- `Monitor`：监控与异常处理接口

## 4. Order

### 4.1 创建订单

- Method：`POST`
- Path：`/api/orders`
- Tag：`Order`
- Summary：创建订单并进入待调度流程

#### Request Body

```json
{
  "externalOrderNo": "EXT202604270001",
  "sourceType": "MANUAL",
  "bizType": "DELIVERY",
  "pickupPointId": 1001,
  "dropoffPointId": 2001,
  "priority": "NORMAL",
  "remark": "test order"
}
```

#### Request 字段

- `externalOrderNo` string required
- `sourceType` string required
- `bizType` string required
- `pickupPointId` long required
- `dropoffPointId` long required
- `priority` string required
- `remark` string optional

#### Response Body

```json
{
  "orderId": 1,
  "orderNo": "ORD202604270001",
  "status": "WAITING_DISPATCH"
}
```

#### 业务规则

- 同一 `sourceType + externalOrderNo` 必须幂等
- 创建成功后必须进入 `WAITING_DISPATCH`

### 4.2 查询订单详情

- Method：`GET`
- Path：`/api/orders/{id}`
- Tag：`Order`
- Summary：查询订单详情

#### Path 参数

- `id` long required

#### Response Body

```json
{
  "orderId": 1,
  "orderNo": "ORD202604270001",
  "status": "DISPATCHED",
  "priority": "NORMAL",
  "dispatchTaskId": 10,
  "vehicleId": 100,
  "pickupPointId": 1001,
  "dropoffPointId": 2001,
  "createdAt": "2026-04-27 10:00:00"
}
```

### 4.3 分页查询订单

- Method：`POST`
- Path：`/api/orders/query`
- Tag：`Order`
- Summary：分页查询订单列表

#### Request Body

```json
{
  "orderNo": "ORD2026",
  "status": "IN_PROGRESS",
  "priority": "HIGH",
  "createdTimeStart": "2026-04-27 00:00:00",
  "createdTimeEnd": "2026-04-27 23:59:59",
  "pageNo": 1,
  "pageSize": 20
}
```

#### Response 字段建议

- `total`
- `records[]`
  - `orderId`
  - `orderNo`
  - `status`
  - `priority`
  - `dispatchTaskId`
  - `vehicleId`
  - `createdAt`

### 4.4 取消订单

- Method：`POST`
- Path：`/api/orders/{id}/cancel`
- Tag：`Order`
- Summary：取消订单

#### Path 参数

- `id` long required

#### Request Body

```json
{
  "cancelReason": "user cancel"
}
```

#### 业务规则

- 默认允许取消 `WAITING_DISPATCH`
- `DISPATCHED` 是否允许取消，由服务层联动校验任务与车辆状态
- `IN_PROGRESS` 默认不允许直接取消

## 5. Dispatch

### 5.1 创建或触发派单

- Method：`POST`
- Path：`/api/dispatch/tasks/{orderId}/assign`
- Tag：`Dispatch`
- Summary：对订单执行自动或手动派单

#### Path 参数

- `orderId` long required

#### Request Body

```json
{
  "assignMode": "AUTO",
  "vehicleId": null,
  "remark": "auto assign"
}
```

#### Request 字段

- `assignMode` string required
  - `AUTO`
  - `MANUAL`
- `vehicleId` long conditional
  - 手动派单必填
- `remark` string optional

#### Response Body

```json
{
  "taskId": 10,
  "taskNo": "DT202604270001",
  "status": "ASSIGNED",
  "vehicleId": 100
}
```

#### 业务规则

- 自动派单只从 `ONLINE + IDLE + 非禁用` 车辆中选择
- 手动派单必须记录操作人和备注

### 5.2 改派任务

- Method：`POST`
- Path：`/api/dispatch/tasks/{taskId}/reassign`
- Tag：`Dispatch`
- Summary：将任务改派到目标车辆

#### Path 参数

- `taskId` long required

#### Request Body

```json
{
  "targetVehicleId": 101,
  "reason": "vehicle switch"
}
```

#### 业务规则

- MVP 默认只允许 `ASSIGNED` 状态改派
- 改派必须记录原车辆、新车辆和原因

### 5.3 取消调度任务

- Method：`POST`
- Path：`/api/dispatch/tasks/{taskId}/cancel`
- Tag：`Dispatch`
- Summary：取消调度任务

#### Path 参数

- `taskId` long required

#### Request Body

```json
{
  "reason": "manual cancel"
}
```

### 5.4 查询调度任务详情

- Method：`GET`
- Path：`/api/dispatch/tasks/{taskId}`
- Tag：`Dispatch`
- Summary：查询调度任务详情

#### Path 参数

- `taskId` long required

#### Response Body

```json
{
  "taskId": 10,
  "taskNo": "DT202604270001",
  "orderId": 1,
  "vehicleId": 100,
  "status": "EXECUTING",
  "failReasonCode": null,
  "failReasonMsg": null,
  "assignTime": "2026-04-27 10:02:00",
  "startTime": "2026-04-27 10:05:00",
  "finishTime": null
}
```

### 5.5 分页查询调度任务

- Method：`POST`
- Path：`/api/dispatch/tasks/query`
- Tag：`Dispatch`
- Summary：分页查询调度任务

#### Request Body

```json
{
  "taskNo": "DT2026",
  "orderNo": "ORD2026",
  "vehicleCode": "V1001",
  "status": "MANUAL_PENDING",
  "manualFlag": true,
  "pageNo": 1,
  "pageSize": 20
}
```

## 6. Vehicle

### 6.1 查询车辆详情

- Method：`GET`
- Path：`/api/vehicles/{id}`
- Tag：`Vehicle`
- Summary：查询车辆详情

#### Path 参数

- `id` long required

#### Response Body

```json
{
  "vehicleId": 100,
  "vehicleCode": "V1001",
  "vehicleName": "Vehicle-01",
  "onlineStatus": "ONLINE",
  "dispatchStatus": "BUSY",
  "currentTaskId": 10,
  "currentOrderId": 1,
  "currentLatitude": 31.2304,
  "currentLongitude": 121.4737,
  "batteryLevel": 86,
  "lastReportTime": "2026-04-27 10:06:00"
}
```

### 6.2 分页查询车辆

- Method：`POST`
- Path：`/api/vehicles/query`
- Tag：`Vehicle`
- Summary：分页查询车辆

#### Request Body

```json
{
  "vehicleCode": "V1",
  "onlineStatus": "ONLINE",
  "dispatchStatus": "IDLE",
  "pageNo": 1,
  "pageSize": 20
}
```

### 6.3 车辆状态回传

- Method：`POST`
- Path：`/api/vehicles/state-report`
- Tag：`Vehicle`
- Summary：接收车辆状态回传

#### Request Body

```json
{
  "vehicleCode": "V1001",
  "onlineStatus": "ONLINE",
  "dispatchStatus": "BUSY",
  "latitude": 31.2304,
  "longitude": 121.4737,
  "batteryLevel": 85,
  "reportTime": "2026-04-27 10:06:00",
  "taskNo": "DT202604270001",
  "orderNo": "ORD202604270001"
}
```

#### 业务规则

- 必须做幂等控制
- 必须校验 `reportTime`，避免旧状态覆盖新状态
- 涉及任务推进时，必须能关联 `taskNo` 或 `orderNo`

## 7. Monitor

### 7.1 查询异常任务

- Method：`POST`
- Path：`/api/monitor/exceptions/query`
- Tag：`Monitor`
- Summary：分页查询异常任务

#### Request Body

```json
{
  "exceptionType": "ASSIGN_FAILED",
  "exceptionStatus": "OPEN",
  "taskNo": "DT2026",
  "orderNo": "ORD2026",
  "vehicleCode": "V1001",
  "pageNo": 1,
  "pageSize": 20
}
```

### 7.2 人工处理异常

- Method：`POST`
- Path：`/api/monitor/exceptions/{id}/resolve`
- Tag：`Monitor`
- Summary：人工处理异常记录

#### Path 参数

- `id` long required

#### Request Body

```json
{
  "resolveAction": "REASSIGN",
  "resolveRemark": "switch to backup vehicle"
}
```

#### 业务规则

- 人工处理必须记录处理人、处理时间和说明
- 人工处理结果必须推进业务状态，不允许只改展示状态

### 7.3 获取看板摘要

- Method：`GET`
- Path：`/api/monitor/dashboard/summary`
- Tag：`Monitor`
- Summary：查询调度看板摘要

#### Response Body

```json
{
  "waitingDispatchCount": 12,
  "executingTaskCount": 18,
  "failedTaskCount": 2,
  "offlineVehicleCount": 3,
  "idleVehicleCount": 15
}
```

## 8. 枚举草案

### 8.1 订单状态

- `CREATED`
- `WAITING_DISPATCH`
- `DISPATCHED`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`
- `FAILED`

### 8.2 调度任务状态

- `PENDING`
- `ASSIGNING`
- `ASSIGNED`
- `EXECUTING`
- `SUCCESS`
- `FAILED`
- `CANCELLED`
- `MANUAL_PENDING`

### 8.3 车辆在线状态

- `ONLINE`
- `OFFLINE`

### 8.4 车辆调度状态

- `IDLE`
- `BUSY`
- `UNAVAILABLE`

### 8.5 异常类型

- `ASSIGN_FAILED`
- `VEHICLE_OFFLINE`
- `TASK_TIMEOUT`
- `STATE_REPORT_ERROR`

## 9. 错误场景建议

- 参数非法：入参缺失、枚举值非法、时间格式非法
- 状态非法：当前状态不允许执行指定操作
- 资源不存在：订单、任务、车辆不存在
- 并发冲突：状态已被其他请求修改
- 幂等冲突：重复创建订单、重复处理回传

## 10. 后续落地建议

- 将本清单进一步转成 `OpenAPI YAML`
- 与前端确认分页结构、时间字段格式、异常码口径
- 在编码前冻结关键状态枚举与业务规则

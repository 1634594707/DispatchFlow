# FSD-Core 前端对接说明

## 1. 文档目标

本文档用于向前端工程师交付当前可用的后端接口契约，覆盖调度员管理台第一阶段需要的订单、任务、异常、车辆、看板能力。

## 2. 基础约定

### 2.1 Base URL

后端统一前缀：

`/api`

### 2.2 统一响应结构

所有接口统一返回：

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "OK",
  "data": {}
}
```

失败时：

```json
{
  "success": false,
  "code": "ERROR_CODE",
  "message": "error message",
  "data": null
}
```

### 2.3 分页结构

分页接口统一返回：

```json
{
  "total": 100,
  "pageNo": 1,
  "pageSize": 20,
  "records": []
}
```

### 2.4 时间字段

当前后端返回 Java 时间对象序列化结果，前端按 ISO 风格字符串处理即可，例如：

- `2026-04-28T18:00:00`

## 3. 页面与接口映射

建议前端按以下页面组织：

1. 订单列表页
2. 订单详情页
3. 调度任务列表页
4. 调度任务详情页
5. 异常列表页
6. 车辆列表页
7. 车辆详情页
8. 调度看板页

## 4. 订单管理

### 4.1 获取订单列表

- Method: `GET`
- Path: `/api/admin/orders`

返回 `data` 示例：

```json
[
  {
    "orderId": 1,
    "orderNo": "ORD202604281800001234",
    "externalOrderNo": "EXT-001",
    "status": "WAITING_DISPATCH",
    "priority": "P1",
    "dispatchTaskId": 10,
    "createdAt": "2026-04-28T18:00:00",
    "updatedAt": "2026-04-28T18:00:10"
  }
]
```

### 4.2 分页查询订单

- Method: `POST`
- Path: `/api/admin/orders/query`

请求体：

```json
{
  "orderNo": "ORD",
  "externalOrderNo": "EXT",
  "status": "WAITING_DISPATCH",
  "priority": "P1",
  "pageNo": 1,
  "pageSize": 20
}
```

返回：

- `PageResponse<OrderAdminListItemResponse>`

### 4.3 获取订单详情

- Method: `GET`
- Path: `/api/admin/orders/{orderId}`

返回 `data` 示例：

```json
{
  "orderId": 1,
  "orderNo": "ORD202604281800001234",
  "externalOrderNo": "EXT-001",
  "sourceType": "MANUAL",
  "bizType": "DELIVERY",
  "pickupPointId": 11,
  "dropoffPointId": 22,
  "priority": "P1",
  "status": "DISPATCHED",
  "dispatchTaskId": 10,
  "remark": "test order",
  "createdAt": "2026-04-28T18:00:00",
  "updatedAt": "2026-04-28T18:01:00"
}
```

## 5. 调度任务管理

### 5.1 获取任务列表

- Method: `GET`
- Path: `/api/admin/tasks`

返回字段：

- `taskId`
- `taskNo`
- `orderId`
- `vehicleId`
- `status`
- `failReasonCode`
- `failReasonMsg`
- `createdAt`
- `updatedAt`

### 5.2 分页查询任务

- Method: `POST`
- Path: `/api/admin/tasks/query`

请求体：

```json
{
  "taskNo": "TSK",
  "orderId": 1,
  "vehicleId": 100,
  "status": "MANUAL_PENDING",
  "manualFlag": true,
  "pageNo": 1,
  "pageSize": 20
}
```

说明：

- 当前后端已接收 `manualFlag`
- 但当前筛选逻辑还没有基于 `manualFlag` 生效，前端先不要把它当成强依赖条件

### 5.3 获取任务详情

- Method: `GET`
- Path: `/api/admin/tasks/{taskId}`

返回 `data` 示例：

```json
{
  "taskId": 10,
  "taskNo": "TSK202604281801001234",
  "orderId": 1,
  "vehicleId": 100,
  "dispatchType": "AUTO",
  "status": "ASSIGNED",
  "failReasonCode": null,
  "failReasonMsg": null,
  "assignTime": "2026-04-28T18:01:00",
  "startTime": null,
  "finishTime": null,
  "manualFlag": 0,
  "retryCount": 0,
  "remark": "auto assign"
}
```

## 6. 异常管理

### 6.1 获取异常列表

- Method: `GET`
- Path: `/api/admin/exceptions`

### 6.2 分页查询异常

- Method: `POST`
- Path: `/api/admin/exceptions/query`

请求体：

```json
{
  "exceptionType": "TASK_EXECUTE_FAILED",
  "exceptionStatus": "OPEN",
  "taskNo": "",
  "orderId": 1,
  "vehicleId": 100,
  "pageNo": 1,
  "pageSize": 20
}
```

返回字段：

- `id`
- `taskId`
- `orderId`
- `vehicleId`
- `exceptionType`
- `exceptionStatus`
- `exceptionMsg`
- `occurTime`
- `resolvedTime`
- `resolverId`
- `resolveRemark`
- `createdAt`
- `updatedAt`

说明：

- 当前后端已接收 `taskNo`
- 但当前筛选逻辑还没有基于 `taskNo` 生效，前端先不要把它当成强依赖条件

### 6.3 处理异常

- Method: `POST`
- Path: `/api/admin/exceptions/{exceptionId}/resolve`

请求体：

```json
{
  "resolverId": "u1001",
  "resolverName": "dispatcherA",
  "action": "MARK_FAILED",
  "remark": "confirmed and closed"
}
```

说明：

- 当前动作会把异常记录置为 `RESOLVED`
- 当前版本主要是异常关闭记录，不会自动联动更多复杂补偿动作

## 7. 车辆管理

### 7.1 获取车辆列表

- Method: `GET`
- Path: `/api/admin/vehicles`

返回字段：

- `vehicleId`
- `vehicleCode`
- `vehicleName`
- `onlineStatus`
- `dispatchStatus`
- `currentTaskId`
- `currentOrderId`
- `batteryLevel`
- `lastReportTime`

### 7.2 分页查询车辆

- Method: `POST`
- Path: `/api/admin/vehicles/query`

请求体：

```json
{
  "vehicleCode": "VH",
  "onlineStatus": "ONLINE",
  "dispatchStatus": "IDLE",
  "pageNo": 1,
  "pageSize": 20
}
```

### 7.3 获取车辆详情

- Method: `GET`
- Path: `/api/admin/vehicles/{vehicleId}`

返回 `data` 示例：

```json
{
  "vehicleId": 100,
  "vehicleCode": "VH-001",
  "vehicleName": "Vehicle 1",
  "vehicleType": "CAR",
  "onlineStatus": "ONLINE",
  "dispatchStatus": "BUSY",
  "currentTaskId": 10,
  "currentOrderId": 1,
  "currentLatitude": 31.2304,
  "currentLongitude": 121.4737,
  "batteryLevel": 86,
  "lastReportTime": "2026-04-28T18:06:00",
  "remark": null
}
```

## 8. 调度看板

### 8.1 获取看板摘要

- Method: `GET`
- Path: `/api/admin/dashboard/summary`

返回 `data` 示例：

```json
{
  "pendingCount": 1,
  "assigningCount": 0,
  "manualPendingCount": 2,
  "executingCount": 3,
  "failedCount": 1,
  "onlineVehicleCount": 10,
  "idleVehicleCount": 5,
  "busyVehicleCount": 5
}
```

## 9. 前端状态枚举

### 9.1 订单状态

- `CREATED`
- `WAITING_DISPATCH`
- `DISPATCHED`
- `IN_PROGRESS`
- `COMPLETED`
- `CANCELLED`
- `FAILED`

### 9.2 调度任务状态

- `PENDING`
- `ASSIGNING`
- `ASSIGNED`
- `EXECUTING`
- `SUCCESS`
- `FAILED`
- `CANCELLED`
- `MANUAL_PENDING`

### 9.3 车辆在线状态

- `ONLINE`
- `OFFLINE`

### 9.4 车辆调度状态

- `IDLE`
- `BUSY`
- `UNAVAILABLE`

## 10. 当前已知限制

1. 分页查询目前是聚合层内存分页，MVP 可用，但不适合大数据量
2. `manualFlag`、`taskNo` 等少数筛选字段已经收参，但部分尚未完全下沉成强筛选
3. 异常处理接口当前以“关闭异常记录”为主，未做更复杂的自动补偿动作
4. 当前没有鉴权，前端联调阶段可直接调用，后续如接登录鉴权需要统一加拦截器和 token 约定

## 11. 推荐前端页面落地顺序

1. 调度看板
2. 调度任务列表
3. 调度任务详情
4. 异常列表
5. 异常处理弹窗
6. 订单列表与详情
7. 车辆列表与详情

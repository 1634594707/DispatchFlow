# Release Checklist

## 1. 环境检查

- [ ] `docker compose up --build` 可正常启动
- [ ] MySQL / Redis / RabbitMQ / 应用容器状态正常
- [ ] `http://localhost:8080/api/health` 返回成功
- [ ] `http://localhost:8080/swagger-ui.html` 可访问
- [ ] `http://localhost:15673` RabbitMQ 管理台可访问

## 2. 数据检查

- [ ] `V1__init_schema.sql` 已执行
- [ ] `V2__dispatch_constraints.sql` 已执行
- [ ] `V3__dispatch_event_outbox.sql` 已执行
- [ ] `t_order`、`t_dispatch_task`、`t_vehicle`、`t_dispatch_event_outbox` 表存在
- [ ] `t_dispatch_task.order_id` 唯一约束生效

## 3. 核心链路检查

- [ ] 可创建订单
- [ ] 可创建调度任务
- [ ] 自动派单成功时订单、任务、车辆状态联动正确
- [ ] 自动派单失败时任务进入 `MANUAL_PENDING`
- [ ] 人工派单可成功分配车辆
- [ ] 车辆回传 `START_EXECUTE` 可推进任务到 `EXECUTING`
- [ ] 车辆回传 `TASK_SUCCESS` 可推进订单到 `COMPLETED`
- [ ] 车辆回传 `TASK_FAILED` 可推进订单到 `FAILED`

## 4. 管理端接口检查

- [ ] `/api/admin/orders` 可返回数据
- [ ] `/api/admin/orders/query` 可分页查询
- [ ] `/api/admin/orders/{orderId}` 可查看订单详情
- [ ] `/api/admin/tasks` 可返回数据
- [ ] `/api/admin/tasks/query` 可分页查询
- [ ] `/api/admin/tasks/{taskId}` 可查看任务详情
- [ ] `/api/admin/exceptions` 可返回数据
- [ ] `/api/admin/exceptions/query` 可分页查询
- [ ] `/api/admin/exceptions/{exceptionId}/resolve` 可关闭异常
- [ ] `/api/admin/vehicles` 可返回数据
- [ ] `/api/admin/vehicles/query` 可分页查询
- [ ] `/api/admin/vehicles/{vehicleId}` 可查看车辆详情
- [ ] `/api/admin/dashboard/summary` 可返回摘要

## 5. 消息与幂等检查

- [ ] 自动派单时可写入 outbox 事件
- [ ] 事件发布成功后 outbox 状态为 `PUBLISHED`
- [ ] 模拟发布失败后 outbox 状态可进入 `FAILED`
- [ ] 车辆重复回传不会重复推进状态
- [ ] 同一任务并发派单不会出现重复分配

## 6. 测试检查

- [ ] `mvn -q -pl fsd-dispatch -am test` 通过
- [ ] `mvn -q -pl fsd-admin-api -am test` 通过
- [ ] `mvn -q -pl fsd-bootstrap -am test` 通过

## 7. 风险确认

- [ ] 已知分页仍为聚合层内存分页
- [ ] 已知异常处理补偿动作仍未补齐
- [ ] 已知未接入鉴权与权限控制
- [ ] 已知消息治理仍是 MVP 骨架，不是完整生产方案

## 8. 对外交付材料

- [ ] 前端对接文档已提供
- [ ] 后端交接文档已提供
- [ ] Docker 启动文档已提供
- [ ] 演示环境地址已确认

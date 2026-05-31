# FSD-Core 后端交接文档

## 1. 文档目标

本文档用于向后续接手的后端工程师说明当前 FSD-Core MVP 后端的实现范围、工程结构、启动方式、测试方式、Docker 部署方式、已知风险和建议下一步。

## 2. 项目定位

当前项目是 `FSD-Core` 的 MVP 阶段后端实现，形态为：

- 模块化单体
- Java 21
- Spring Boot 3.x
- MyBatis-Plus
- MySQL 8
- Redis 7
- RabbitMQ 3.x

核心业务闭环：

`订单进入 -> 待调度 -> 自动/人工派单 -> 车辆执行 -> 状态回传 -> 异常处理 -> 完成/失败`

## 3. 模块结构

后端模块：

- `fsd-common`
- `fsd-order`
- `fsd-dispatch`
- `fsd-vehicle`
- `fsd-admin-api`
- `fsd-bootstrap`

模块职责简述：

### 3.1 fsd-common

- 通用返回体 `ApiResponse`
- 通用分页体 `PageResponse`
- 业务异常 `BusinessException`
- 状态枚举

### 3.2 fsd-order

- 订单创建
- 订单详情查询
- 订单状态流转服务
- 管理端订单查询服务

### 3.3 fsd-dispatch

- 调度任务创建
- 自动派单
- 人工派单
- 调度任务状态校验
- 调度异常记录
- 操作日志记录
- 车辆回传驱动任务推进
- Redis 短锁
- Redis 幂等
- RabbitMQ 事件发布
- Outbox 重试骨架

### 3.4 fsd-vehicle

- 车辆实体与查询
- 车辆可分配性校验
- 车辆状态快照更新
- 管理端车辆查询服务

### 3.5 fsd-admin-api

- 管理台聚合接口
- 订单、任务、异常、车辆、看板查询入口
- 异常处理入口

### 3.6 fsd-bootstrap

- Spring Boot 启动入口
- Mapper 扫描
- 全局异常处理
- 集成测试入口

## 4. 当前已完成能力

### 4.1 订单域

已完成：

- 创建订单
- 查询订单详情
- 订单状态迁移服务

当前状态迁移已覆盖：

- `WAITING_DISPATCH -> DISPATCHED`
- `DISPATCHED -> IN_PROGRESS`
- `IN_PROGRESS -> COMPLETED`
- `WAITING_DISPATCH / DISPATCHED / IN_PROGRESS -> FAILED`

### 4.2 调度域

已完成：

- 创建调度任务
- 自动派单
- 人工派单
- 任务详情
- 人工待处理列表
- 调度态势摘要
- 异常记录与异常关闭

自动派单规则：

- 只从 `ONLINE + IDLE` 车辆中选车
- 无可用车默认进入 `MANUAL_PENDING`
- 车辆占用冲突也进入 `MANUAL_PENDING`

### 4.3 车辆域

已完成：

- 车辆可分配查询
- 车辆占用与释放
- 车辆状态回传

已支持的回传类型：

- `START_EXECUTE`
- `TASK_SUCCESS`
- `TASK_FAILED`
- `OFFLINE`

### 4.4 异常与日志

已完成：

- `t_dispatch_exception_record`
- `t_dispatch_task_operate_log`
- 异常创建
- 异常关闭
- 状态迁移操作日志

### 4.5 Redis / RabbitMQ / 幂等

已完成：

- 任务维度短锁
- 车辆回传幂等键
- 事件模型
- RabbitMQ 发布挂点
- Outbox 表
- 失败重试骨架
- 消费端幂等守卫

### 4.6 管理端接口

已完成：

- 订单列表
- 订单分页查询
- 订单详情
- 任务列表
- 任务分页查询
- 任务详情
- 异常列表
- 异常分页查询
- 异常处理
- 车辆列表
- 车辆分页查询
- 车辆详情
- 看板摘要

前端对接文档见：

- [frontend-api-handoff.md](D:\Administrator\Desktop\Project\DispatchFlow\docs\docs\frontend-api-handoff.md)

## 5. 重要工程修正

### 5.1 Mapper 扫描范围已收窄

当前启动类不再扫描整个 `com.fsd`，只扫描真正的 mapper 包，避免普通接口被误注册为 MyBatis Mapper。

### 5.2 Docker 已补齐

当前仓库已提供：

- `Dockerfile`
- `docker-compose.yml`
- `.dockerignore`

Docker 启动文档见：

- [docker-deploy.md](D:\Administrator\Desktop\Project\DispatchFlow\docs\docs\docker-deploy.md)

## 6. 数据库脚本

当前初始化脚本：

- `sql/init/V1__init_schema.sql`
- `sql/init/V2__dispatch_constraints.sql`
- `sql/init/V3__dispatch_event_outbox.sql`

说明：

- `V1` 初始化核心业务表
- `V2` 增加调度相关唯一约束和索引
- `V3` 增加事件 outbox 表

## 7. 启动方式

### 7.1 本地启动

在 `DISPATCHFLOW` 目录下：

```bash
mvn clean package
mvn -pl fsd-bootstrap spring-boot:run
```

默认依赖：

- MySQL
- Redis
- RabbitMQ

### 7.2 Docker 启动

在 `DISPATCHFLOW` 目录下：

```bash
docker compose up --build
```

默认宿主机端口：

- 应用：`8080`
- MySQL：`3307`
- Redis：`6380`
- RabbitMQ：`5673`
- RabbitMQ 管理台：`15673`

## 8. 测试情况

### 8.1 已完成测试

单元测试：

- 自动派单
- 人工派单
- 车辆回传
- 异常处理
- 管理端 controller

集成测试：

- 主链路成功流转
- 并发抢同一车辆

### 8.2 常用测试命令

```bash
mvn -q -pl fsd-dispatch -am test
```

```bash
mvn -q -pl fsd-admin-api -am test
```

```bash
mvn -q -pl fsd-bootstrap -am test
```

## 9. 当前已知限制

### 9.1 查询分页

当前 `fsd-admin-api` 的分页查询是聚合层内存分页，不是数据库原生分页。

影响：

- MVP 阶段可用
- 数据量变大后性能会明显下降

建议：

- 后续改成 MyBatis 级别分页查询

### 9.2 筛选字段

少数字段已收参但未完全下沉为严格筛选条件，例如：

- `manualFlag`
- `taskNo` 在异常查询中的联动过滤

建议：

- 后续统一补齐数据库级筛选逻辑

### 9.3 异常处理动作

当前异常处理接口主要完成：

- 将异常记录关闭
- 记录处理人、动作、备注

但尚未做复杂补偿，例如：

- 自动改派
- 自动重试派单
- 自动任务回滚

### 9.4 消息可靠性

当前已具备：

- outbox 持久化
- after-commit 发布
- 失败重试骨架
- 消费端幂等守卫

但还不算完整生产级方案，仍缺：

- 死信队列治理
- 消费失败人工补偿工具
- 事件审计查询接口
- 重试告警

### 9.5 鉴权

当前没有登录态和权限控制，所有管理端接口默认可直接访问。

## 10. 建议下一步

建议后续优先级：

1. 将管理端分页查询改成数据库分页
2. 补齐异常处理补偿动作
3. 接入鉴权与权限控制
4. 补日志追踪透传和查询能力
5. 补监控告警和消息失败治理
6. 配合前端完成联调

## 11. 交付结论

当前后端已经具备：

- 核心业务主链路
- Docker 启动方式
- 管理端可对接接口
- 基础测试覆盖
- Redis / RabbitMQ / Outbox 的 MVP 骨架

从交接角度看，当前状态已经足够支持：

- 前端管理台第一阶段开发
- 本地联调
- Docker 化演示环境

如果要进一步进入准生产阶段，需要优先处理查询分页、鉴权、消息治理和异常补偿这几块。

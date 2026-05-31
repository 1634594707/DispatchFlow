# FSD-Core 后端模块目录结构草案

## 1. 文档目标

本文档定义 `FSD-Core MVP` 后端工程的推荐目录结构、模块职责、分层规范和初始化阶段的组织方式，供项目脚手架搭建时直接采用。

## 2. 总体结构

MVP 阶段采用 `Maven 多模块 + 模块化单体`。

推荐根目录结构：

```text
fsd-core-server
├─ pom.xml
├─ README.md
├─ docs/
├─ sql/
│  └─ init/
│     └─ V1__init_schema.sql
├─ fsd-bootstrap/
├─ fsd-common/
├─ fsd-order/
├─ fsd-dispatch/
├─ fsd-vehicle/
└─ fsd-admin-api/
```

## 3. 根工程职责

根工程负责：

- 聚合模块
- 统一依赖版本
- 统一插件管理
- 统一 Java 版本
- 统一编码、测试和覆盖率约束

建议根 `pom.xml` 负责：

- `dependencyManagement`
- `pluginManagement`
- `modules`
- 统一 `java.version=21`

## 4. 模块划分

### 4.1 fsd-bootstrap

职责：

- Spring Boot 启动入口
- 扫描与装配
- 环境配置加载
- 中间件配置装配

建议结构：

```text
fsd-bootstrap
└─ src/main/java/com/fsd/bootstrap
   ├─ FsdCoreApplication.java
   └─ config/
```

说明：

- 不放业务逻辑
- 不放复杂工具类

### 4.2 fsd-common

职责：

- 通用异常
- 公共枚举
- 统一返回结构适配
- 公共工具类
- 通用日志上下文
- 基础配置对象

建议结构：

```text
fsd-common
└─ src/main/java/com/fsd/common
   ├─ constant/
   ├─ enums/
   ├─ exception/
   ├─ model/
   ├─ util/
   └─ log/
```

说明：

- `common` 只能承载横切能力
- 禁止把业务逻辑堆进 `common`

### 4.3 fsd-order

职责：

- 订单创建
- 订单查询
- 订单取消
- 订单状态流转

建议结构：

```text
fsd-order
└─ src/main/java/com/fsd/order
   ├─ controller/
   ├─ service/
   │  ├─ impl/
   │  └─ domain/
   ├─ mapper/
   ├─ entity/
   ├─ dto/
   ├─ vo/
   ├─ convert/
   └─ enums/
```

说明：

- `controller` 仅处理请求转换和响应组装
- `service` 承担业务逻辑
- `mapper` 只负责持久化

### 4.4 fsd-dispatch

职责：

- 调度任务生成
- 自动派单
- 手动派单
- 改派
- 调度状态流转
- 异常转人工处理

建议结构：

```text
fsd-dispatch
└─ src/main/java/com/fsd/dispatch
   ├─ controller/
   ├─ service/
   │  ├─ impl/
   │  ├─ domain/
   │  └─ strategy/
   ├─ mapper/
   ├─ entity/
   ├─ dto/
   ├─ vo/
   ├─ convert/
   ├─ enums/
   ├─ mq/
   └─ cache/
```

说明：

- `strategy/` 用于后续扩展派单规则，不在 MVP 阶段过度设计
- `mq/` 放事件发布和消费逻辑
- `cache/` 放 Redis 键与缓存访问封装

### 4.5 fsd-vehicle

职责：

- 车辆基础信息
- 在线状态维护
- 调度可用状态维护
- 状态回传处理

建议结构：

```text
fsd-vehicle
└─ src/main/java/com/fsd/vehicle
   ├─ controller/
   ├─ service/
   │  ├─ impl/
   │  └─ domain/
   ├─ mapper/
   ├─ entity/
   ├─ dto/
   ├─ vo/
   ├─ convert/
   ├─ enums/
   └─ cache/
```

### 4.6 fsd-admin-api

职责：

- 调度后台接口聚合
- 看板摘要
- 异常任务列表
- 人工处理入口

建议结构：

```text
fsd-admin-api
└─ src/main/java/com/fsd/admin
   ├─ controller/
   ├─ service/
   │  └─ impl/
   ├─ dto/
   └─ vo/
```

说明：

- `admin-api` 主要面向页面聚合，不应沉淀深业务逻辑

## 5. 推荐分层规则

统一分层：

- `controller`
- `service`
- `mapper`
- `entity`
- `dto`
- `vo`
- `convert`

规则：

- `Controller -> Service -> Mapper`
- 禁止跨层直接调用
- 禁止直接把 `Entity` 返回给前端
- 外部请求对象必须使用 `DTO`
- 页面响应对象必须使用 `VO`

## 6. 包命名建议

统一基础包名建议：

```text
com.fsd
```

模块包名建议：

- `com.fsd.bootstrap`
- `com.fsd.common`
- `com.fsd.order`
- `com.fsd.dispatch`
- `com.fsd.vehicle`
- `com.fsd.admin`

说明：

- 包名要表达业务语义
- 禁止使用不清晰缩写

## 7. 配置文件建议

推荐配置目录：

```text
fsd-bootstrap
└─ src/main/resources
   ├─ application.yml
   ├─ application-dev.yml
   ├─ application-test.yml
   └─ mapper/
```

建议按环境拆分：

- `dev`
- `test`
- `prod`

说明：

- 中间件连接配置统一放在 `bootstrap` 层
- 业务模块不单独维护分散配置

## 8. 测试结构建议

每个业务模块建议至少保留：

```text
src/test/java/com/fsd/{module}
├─ service/
├─ controller/
└─ integration/
```

说明：

- `service/` 放核心业务单测
- `integration/` 放主链路集成测试

## 9. SQL 与文档目录建议

推荐：

```text
sql/
└─ init/
   └─ V1__init_schema.sql

docs/
└─ fsd-core-mvp/
```

说明：

- 初始化 SQL 与项目文档都放在仓库内统一管理
- 避免把库表设计散落在聊天记录或外部工具中

## 10. 初始化阶段必须具备的内容

项目骨架初始化后，至少应包含：

- Maven 根工程
- 6 个后端模块
- 基础启动类
- 通用异常与日志框架
- 核心枚举
- 建表 SQL
- 基础配置文件
- 示例 Controller 和健康检查接口

## 11. 目录结构结论

这套目录结构的目标是：

- 在 MVP 阶段保持边界清晰
- 降低后续模块膨胀和耦合失控风险
- 为未来拆分服务预留自然边界

后续如果你开始正式搭工程，这份结构可以直接作为初始化脚手架蓝图。

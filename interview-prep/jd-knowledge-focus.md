# JD 对应的重点学习知识

## 学习目标

目标不是泛学 Java，而是围绕这份 JD 做定向补强。

JD 核心关键词：

- AGV / 无人车
- Java
- Spring Boot
- Spring Cloud
- MySQL
- Redis
- RabbitMQ
- 选车算法
- 状态机
- 事件驱动

## P0 必须补强

### 1. Java 基础

重点学习：

- 集合体系
- `HashMap`、`ConcurrentHashMap`
- 线程与线程池
- `synchronized`
- `ReentrantLock`
- `volatile`
- JVM 内存结构
- GC 基本概念

为什么重要：

这部分会直接决定你能不能讲清：

- 并发派车保护
- 运行时状态缓存
- 定时调度执行
- 基础后端能力

### 2. Spring Boot

重点学习：

- IOC / DI
- AOP
- `@Transactional`
- Controller / Service / Mapper 分层
- 参数校验
- 全局异常处理
- 定时任务

为什么重要：

这个项目的大部分后端结构都建立在这些能力之上。

### 3. MySQL

重点学习：

- 主键索引
- 普通索引
- 联合索引
- 回表
- 最左前缀
- SQL 执行计划
- 事务隔离级别
- 乐观锁 / 悲观锁基础

为什么重要：

面试官很可能会问：

- 订单表、任务表怎么查
- 为什么要建索引
- 并发派车时怎么避免冲突

### 4. Git

重点学习：

- `commit`
- `push`
- `pull`
- `merge`
- 分支协作基本流程

为什么重要：

JD 里明确写了会用 Git，而且这是工程协作最低要求。

## P1 强烈建议补强

### 5. Redis

重点学习：

- 缓存使用场景
- `setIfAbsent`
- 过期时间
- 分布式锁
- 幂等 key

为什么重要：

当前项目里已经有：

- 任务锁抽象
- 事件消费幂等
- 回报幂等

这些都很适合用 Redis 讲。

### 6. RabbitMQ

重点学习：

- Exchange
- Queue
- Routing Key
- 消息确认机制
- 重试
- 死信
- 重复消费
- 消息可靠性

为什么重要：

当前项目已经用了 RabbitMQ + Outbox，这是你对这个 JD 的直接加分项。

### 7. 调度系统核心概念

重点学习：

- 选车策略
- 最近车策略
- 可分配车辆过滤
- 状态机设计
- 低电量返充
- 异常降级
- 人工介入
- 故障重调度

为什么重要：

这部分是这份 JD 的业务核心，不是普通后台项目会考的内容。

## P2 面试加分项

### 8. Spring Cloud 基础

重点学习：

- 注册发现
- 配置中心
- 网关
- 服务调用
- 熔断限流基础概念

为什么重要：

即使当前项目不是完整 Spring Cloud 微服务，你也要能讲“如果扩成微服务怎么拆”。

### 9. 分布式与一致性

重点学习：

- Outbox 模式
- 幂等设计
- 至少一次投递
- 最终一致性
- 分布式锁

为什么重要：

这正好和当前项目的事件驱动设计强相关。

### 10. 实时通信

重点学习：

- WebSocket
- SSE
- 轮询与推送差异

为什么重要：

大屏监控类系统里，这类问题很容易被追问。

## 最推荐的结合项目学习顺序

不要脱离代码硬学，建议按项目代码带着学。

### 第一阶段

先结合这些代码学业务：

1. `DispatchTaskServiceImpl`
2. `ParkPilotSimulationServiceImpl`
3. `VehicleReportServiceImpl`
4. `RabbitDispatchEventPublisher`

### 第二阶段

围绕这些代码补基础：

1. Java 并发
2. Spring 事务
3. MySQL 索引
4. Redis 分布式锁
5. RabbitMQ 可靠消息

### 第三阶段

再补架构表达：

1. Spring Cloud 拆分思路
2. Outbox 模式
3. 调度系统异常处理

## 面试前最该掌握的知识点清单

如果时间有限，至少要能讲清：

- 什么是自动派车
- 什么是最近车策略
- 什么是车辆状态机
- 为什么要低电量返充
- 为什么需要任务锁
- RabbitMQ 在系统里干什么
- 什么是 Outbox
- Redis 为什么能做幂等和锁
- MySQL 索引为什么重要
- 如果扩成微服务怎么拆

## 一句话总结

你现在最该补的，不是泛知识，而是：

“用 Java、Spring、MySQL、Redis、RabbitMQ 把一个无人车调度系统核心链路讲清楚的能力。”

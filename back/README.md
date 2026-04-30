# FSD-Core MVP

FSD-Core 是商用无人配送车辆智能调度平台。

当前目录为 `MVP` 阶段后端工程骨架，采用：

- `Java 21`
- `Spring Boot 3`
- `MyBatis-Plus`
- `MySQL`
- `Redis`
- `RabbitMQ`
- `Maven 多模块 + 模块化单体`

## 模块

- `fsd-common`：通用枚举、异常、响应模型、日志上下文
- `fsd-order`：订单域
- `fsd-dispatch`：调度域
- `fsd-vehicle`：车辆域
- `fsd-admin-api`：管理端接口聚合
- `fsd-bootstrap`：启动模块

## 初始化

1. 安装 `Maven 3.9+` 并加入 `PATH`
2. 初始化数据库：执行 [sql/init/V1__init_schema.sql](sql/init/V1__init_schema.sql)
3. 启动中间件：`MySQL`、`Redis`、`RabbitMQ`
4. 执行：

```bash
mvn clean package
```

5. 运行启动模块：

```bash
mvn -pl fsd-bootstrap -am spring-boot:run
```

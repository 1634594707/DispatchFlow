# DispatchFlow

DispatchFlow 是一个面向园区无人配送场景的调度演示项目，当前包含：

- 管理端前端大屏与业务页面
- Spring Boot 后端聚合服务
- 园区车辆仿真、订单调度、车辆状态回传
- 园区简化下单接口

当前项目适合的运行方式是：

- 前端本地或静态部署
- 后端独立运行
- MySQL / Redis / RabbitMQ 作为依赖服务单独运行

## 目录结构

```text
DispatchFlow/
├─ front/          Vue 3 + Vite 前端
├─ back/           Java Spring Boot 多模块后端
├─ docs/           需求与任务文档
└─ README.md
```

后端模块说明：

- `fsd-common`：公共模型、枚举、异常
- `fsd-order`：订单域
- `fsd-dispatch`：调度域、园区仿真、路径规划
- `fsd-vehicle`：车辆域、车辆上报
- `fsd-admin-api`：管理端聚合接口
- `fsd-bootstrap`：启动模块

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Ant Design Vue、Leaflet
- 后端：Java 21、Spring Boot 3.3、MyBatis-Plus
- 中间件：MySQL、Redis、RabbitMQ

## 本地开发

### 1. 前置依赖

- Node.js 18+
- JDK 21
- Maven 3.9+
- Docker Desktop 或本机安装的 MySQL / Redis / RabbitMQ

### 2. 启动依赖服务

当前后端默认按本地 Docker 端口读取：

- MySQL：`127.0.0.1:3307`
- Redis：`127.0.0.1:6380`
- RabbitMQ：`127.0.0.1:5673`

如果你继续使用 Docker 跑依赖，保持这些端口即可。

数据库默认配置在：

- [application.yml](D:\Administrator\Desktop\Project\DispatchFlow\back\fsd-bootstrap\src\main\resources\application.yml)

初始化数据库后再启动后端。SQL 文件位于：

- `back/sql/`

### 3. 启动后端

注意：根 `pom.xml` 是聚合工程，不能直接在 `back/` 根目录执行 `mvn spring-boot:run`。

正确方式：

```bash
cd back
mvn -pl fsd-bootstrap -am spring-boot:run
```

默认端口：

- 后端：`http://localhost:8080`
- Swagger：`http://localhost:8080/swagger-ui.html`
- OpenAPI：`http://localhost:8080/api-docs`

### 4. 启动前端

```bash
cd front
npm install
npm run dev
```

默认端口：

- 前端：`http://localhost:3000`

Vite 已配置代理：

- `/api -> http://localhost:8080`

配置文件：

- [vite.config.ts](D:\Administrator\Desktop\Project\DispatchFlow\front\vite.config.ts)

## 当前园区相关接口

### 查询接口

- `GET /api/admin/park/layout`
- `GET /api/admin/park/stations`
- `GET /api/admin/park/vehicles`
- `GET /api/admin/park/orders`

### 下单接口

- `POST /api/admin/park/orders`

这是园区简化下单接口，一次完成：

- 创建订单
- 创建调度任务
- 自动分配车辆

请求示例：

```json
{
  "externalOrderNo": "PARK-DEMO-001",
  "pickupStationId": 101,
  "dropoffStationId": 201,
  "priority": "P1",
  "remark": "mobile order"
}
```

### 车辆大屏新增字段

`GET /api/admin/park/vehicles` 当前已包含：

- `runtimeStage`
- `targetCode`
- `targetType`
- `charging`
- `lowBattery`

当前运行阶段可能值包括：

- `STANDBY`
- `TO_PICKUP`
- `LOADING`
- `TO_DROPOFF`
- `UNLOADING`
- `TO_CHARGING`
- `CHARGING`
- `RETURNING_TO_STANDBY`
- `OFFLINE`

详细任务流见：

- [park-pilot-task-flow.md](D:\Administrator\Desktop\Project\DispatchFlow\docs\park-pilot-task-flow.md)

## 测试

推荐命令：

```bash
cd back
mvn -pl fsd-admin-api -am test
mvn -pl fsd-bootstrap -am test
```

## 生产上线建议

### 最简单的上线结构

一台服务器、一个公网 IP，建议这样部署：

1. Nginx
2. 前端静态文件
3. 后端 Jar
4. MySQL
5. Redis
6. RabbitMQ

推荐访问方式：

- `http://你的IP/` -> 前端页面
- `http://你的IP/api/...` -> 后端接口，由 Nginx 反向代理到 `127.0.0.1:8080`

这样前端和后端共用一个 IP，对手机访问最直接。

### 推荐部署形态

- 前端：`npm run build` 后产出 `front/dist/`，交给 Nginx 托管
- 后端：`mvn -pl fsd-bootstrap -am package`，运行 `fsd-bootstrap` 产出的 Jar
- 依赖服务：可继续使用 Docker，也可改成本机服务

### Nginx 反向代理思路

建议路由规则：

- `/` -> 前端静态文件目录
- `/api/` -> `http://127.0.0.1:8080`

这样手机浏览器、管理端、后续 App 都只需要访问同一个域名或同一个 IP。

## 手机下单怎么做

### 不建议一开始就直接做原生 App

如果你现在目标是尽快上线并验证业务，优先级应该是：

1. 先做手机 H5 下单页
2. 让 H5 直接调用 `POST /api/admin/park/orders`
3. 后面再决定是否封装成 App

原因很简单：

- 一台服务器一个 IP 完全够用
- 手机浏览器直接访问就能下单
- 开发成本远低于 Android / iOS 双端原生
- 后端接口可以保持不变

### 最简单落地方案

方案 A：手机网页下单

- 单独做一个移动端页面
- 部署在同一台服务器上
- 手机访问 `http://你的IP/mobile` 或 `http://你的IP/order`
- 页面调用 `/api/admin/park/stations` 获取站点
- 页面调用 `/api/admin/park/orders` 提交订单

这是当前最适合你的方案。

### 如果后面一定要“App”

你有三种选择：

1. H5 页面直接用
2. H5 外包成壳 App
3. UniApp / Flutter 做正式 App

建议顺序：

1. 先做 H5
2. 验证下单流程、调度链路、异常处理
3. 再决定是否封装成 App

### 单服务器单 IP 的访问方式

只要服务器公网可访问，你就可以：

- 手机浏览器直接访问 `http://你的IP`
- H5 下单页请求同域 `/api/admin/park/orders`
- 不需要额外第二台服务器

真正要注意的是：

- 服务器安全组开放 `80` 和必要端口
- 后端不要直接裸露在公网高危端口上，尽量走 Nginx
- 最好尽快绑定域名并上 HTTPS

## 下一步建议

如果你准备进入上线阶段，建议按这个顺序推进：

1. 做一个移动端简化下单页
2. 用同一台服务器部署前端静态资源和后端服务
3. 用 Nginx 统一 `/` 和 `/api`
4. 做最基本的登录或下单鉴权
5. 再考虑封装成 App

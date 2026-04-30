# GitHub 仓库展示检查

## 当前结论

当前 GitHub 仓库结构已经适合投递展示。

仓库保留了：

- `front/`
- `back/`
- `README.md`
- 根目录必要配置文件

仓库没有包含：

- `docs/`
- `node_modules/`
- `dist/`
- `target/`
- 本地日志
- 本地工具目录

这意味着仓库展示面是干净的，不会让面试官第一眼看到一堆无关文件。

## 当前优点

### 1. 目录结构清楚

根目录只有前端、后端和说明文件，结构很直观：

- `front/`：前端项目
- `back/`：后端多模块项目
- `README.md`：项目说明

### 2. README 已适合面试展示

根 `README.md` 已经重写，重点突出：

- 订单驱动调度
- 自动派车
- 最近车策略
- 车辆状态机
- 低电量返充
- 车辆回报驱动任务状态
- RabbitMQ + Outbox

### 3. 项目定位明确

现在这个仓库更像：

“一个面向园区短驳配送场景的无人车调度系统原型”

而不是：

“一个有地图和页面的演示项目”

## 还可以继续优化的点

这些不是必须，但后续可以继续补。

### P1

- 重写 `back/README.md`
- 补 2 到 4 张项目运行截图
- 补一个更短的演示说明

### P2

- 补一份系统架构图
- 补一份状态机图
- 补一份事件流转图

## 当前最值得给面试官看的代码入口

推荐阅读顺序：

1. `back/fsd-admin-api/.../AdminDispatchController.java`
2. `back/fsd-dispatch/.../ParkPilotCommandServiceImpl.java`
3. `back/fsd-dispatch/.../DispatchTaskServiceImpl.java`
4. `back/fsd-dispatch/.../ParkPilotSimulationServiceImpl.java`
5. `back/fsd-dispatch/.../VehicleReportServiceImpl.java`
6. `back/fsd-dispatch/.../RabbitDispatchEventPublisher.java`

## 一句话评价

当前仓库已经可以作为后端岗位投递作品使用，最重要的展示问题已经不是“仓库乱不乱”，而是“你能不能把核心代码讲清楚”。

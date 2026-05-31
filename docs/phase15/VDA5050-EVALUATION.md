# VDA5050 MQTT 适配评估

> Phase 15.2 · 最后更新：2026-05-31  
> 状态：**MVP 已实现**（MQTT state 入站 + order 出站 + 管理端配置 UI + 本地联调栈）

---

## 1. 背景

[VDA5050](https://www.vda5050.com/) 是 AGV/AMR 与主控系统（Master Control）之间的开放接口标准，v2 采用 **MQTT** 传输 JSON 消息。DispatchFlow 作为 FMS 调度中台，需在不破坏现有 `FleetAdapterRegistry` 架构的前提下接入该协议。

## 2. 架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 接入模式 | 新增 `VehicleLinkMode.VDA5050` | 与 HTTP 遥测 `REAL` 分离，便于按协议独立演进 |
| 运行态写入 | 复用 `RealFleetAdapter` 逻辑 | Redis FleetRuntime、轨迹、换电协调与 REAL 一致 |
| 指令下发 | 派车时 MQTT 发布 `order` + 保留 `t_vehicle_command` | 与 REAL 轮询网关并存；MQTT 不可用时命令仍落库 |
| MQTT 客户端 | Eclipse Paho | 轻量、无 Spring Integration 额外依赖 |
| 启用方式 | `fsd.vda5050.mqtt.enabled` 配置开关 | 默认关闭，不影响现有部署 |

## 3. Topic 约定（v2）

```text
{interfaceName}/{manufacturer}/{serialNumber}/state    AGV → FMS
{interfaceName}/{manufacturer}/{serialNumber}/order    FMS → AGV
```

默认 `interfaceName = uagv/v2`。FMS 订阅 `uagv/v2/+/+/state`，按 topic 解析 manufacturer / serialNumber 匹配 `t_vehicle`。

## 4. 已实现能力

| 能力 | 实现 |
|------|------|
| State 入站 | `Vda5050MqttGateway` → `Vda5050StateIngestService` |
| 坐标/SOC/阶段映射 | `Vda5050StateMapper`（driving→EN_ROUTE, charging→CHARGING） |
| Order 出站 | `Vda5050OrderPublisher` + `Vda5050OrderBuilder`（pickup/dropoff 节点） |
| 车辆绑定 | `t_vehicle.vda_manufacturer / vda_serial_number / vda_interface_name`（V19） |
| 管理端 UI | 车辆新建/编辑：VDA5050 模式 + manufacturer / serialNumber |
| 本地联调 | `docker-compose.mqtt.yml` + `scripts/vda5050-sim-agv.py` |

## 5. 代码锚点

| 组件 | 路径 |
|------|------|
| MQTT 网关 | `back/fsd-dispatch/.../vda5050/Vda5050MqttGateway.java` |
| State 映射 | `Vda5050StateMapper.java` |
| Fleet 适配器 | `Vda5050FleetAdapter.java` |
| 配置 | `application.yml` → `fsd.vda5050.mqtt.*` |
| SQL | `back/sql/migrations/V19__vda5050_vehicle_binding.sql` |

## 6. 本地联调步骤

```bash
# 1a. 仅启动 Mosquitto（不构建后端，适合 Docker Hub 不稳定时）
cd back
docker compose -f docker-compose.mqtt.yml up -d

# 1b. 全栈 + MQTT（需已能拉取 maven 镜像）
# docker compose -f docker-compose.yml -f docker-compose.mqtt.yml up -d
# 并设置 FSD_VDA5050_MQTT_ENABLED=true

# 2. 补跑 V19 迁移（已有数据卷时）
docker cp sql/migrations/V19__vda5050_vehicle_binding.sql fsd-mysql:/tmp/V19.sql
docker exec fsd-mysql sh -c "mysql -uroot -proot --default-character-set=utf8mb4 fsd_core < /tmp/V19.sql"

# 3. 启动模拟 AGV（宿主机）
pip install paho-mqtt
python scripts/vda5050-sim-agv.py --broker tcp://127.0.0.1:1883

# 4. 验证
# - 监控大屏应看到 VDA5050-001 上线、位置刷新
# - 派车后 sim 脚本打印 received order，车辆 driving=true
```

## 7. 已知限制（后续迭代）

- 未实现 `instantActions`、`connection`、`visualization` 通道
- Order 节点为站点坐标直映射，未走路网/edge 精细编排
- 未对接 VDA5050 完整 action 状态机与 orderUpdate 增量
- 无 MQTT TLS / 用户名密码（生产需 broker 鉴权）
- 1000+ 车规模需独立 MQTT 集群与 topic 分区（见 ROADMAP 15.3）

## 8. 结论

**MVP 可行且已落地 demo 链路**，满足 M6「VDA5050 demo」验收。规模化与完整协议覆盖归入 Phase 15.3 及后续迭代。

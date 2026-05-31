# Park Pilot Task Flow

## 目标确认

- [x] 车辆行为从“随机巡航演示”调整为“订单驱动执行”
- [x] 订单完成后增加电量判断逻辑
- [x] 低电量车辆自动回充电位
- [x] 提供可用的下单入口，能直接联动后端调度
- [ ] 大屏完整展示“下单 -> 取货 -> 送货 -> 返航/充电”的全流程

## 一、业务规则梳理

- [x] 明确车辆空闲时默认行为：默认回待命位，不再随机巡航
- [x] 明确“有订单时”的执行流程：接单 -> 前往取货点 -> 装货 -> 前往送货点 -> 卸货 -> 完成
- [x] 明确订单完成后的电量判断规则：低电量去充电，否则回待命位
- [x] 明确低电量阈值：读取 `fsd.park.simulation.low-battery-threshold`
- [x] 明确充电完成后的返回状态：充满后回待命位
- [x] 明确无订单但低电量时主动回充
- [x] 明确待命位/充电位分配策略：`parking-spots` 中非 `C*` 点作为待命位，`C*` 点作为充电位

## 二、后端调度逻辑

- [x] 调整园区模拟逻辑，关闭空闲随机巡航
- [x] 增加 `STANDBY` 状态
- [x] 增加 `TO_PICKUP` 状态
- [x] 增加 `LOADING` 状态
- [x] 增加 `TO_DROPOFF` 状态
- [x] 增加 `UNLOADING` 状态
- [x] 增加 `TO_CHARGING` 状态
- [x] 增加 `CHARGING` 状态
- [x] 增加 `RETURNING_TO_STANDBY` 状态
- [x] 订单完成后根据电量决定去待命还是去充电
- [x] 无订单且低电量时自动触发回充
- [x] 车辆充电时逐步恢复电量
- [x] 充电完成后恢复可调度待命状态
- [x] 保证调度状态、订单状态、车辆状态基本一致

## 三、后端接口

### 已可直接给前端对接

- [x] `GET /api/admin/park/layout`
  - 用途：获取园区底图、道路、站点、停车位
- [x] `GET /api/admin/park/stations`
  - 用途：获取可下单站点
- [x] `GET /api/admin/park/vehicles`
  - 用途：获取车辆当前位置、轨迹、运行阶段
  - 新增字段：`targetCode`、`targetType`、`charging`、`lowBattery`
- [x] `GET /api/admin/park/orders`
  - 用途：获取园区订单链路和运行阶段

### 新增接口

- [x] `POST /api/admin/park/orders` `NEW`
  - 用途：园区简化下单，一次完成“创建订单 + 创建调度任务 + 自动分配车辆”
  - 请求体：

```json
{
  "externalOrderNo": "PARK-DEMO-001",
  "pickupStationId": 101,
  "dropoffStationId": 201,
  "priority": "P1",
  "remark": "demo"
}
```

  - 返回字段：

```json
{
  "orderId": 1,
  "orderNo": "ORD-202604300001",
  "orderStatus": "DISPATCHED",
  "taskId": 2,
  "taskNo": "TASK-202604300001",
  "taskStatus": "ASSIGNED",
  "vehicleId": 3,
  "message": "Vehicle assigned"
}
```

### 字段说明

- [x] `GET /api/admin/park/vehicles` 的 `runtimeStage` 目前可能出现：
  - `STANDBY`
  - `TO_PICKUP`
  - `LOADING`
  - `TO_DROPOFF`
  - `UNLOADING`
  - `TO_CHARGING`
  - `CHARGING`
  - `RETURNING_TO_STANDBY`
  - `OFFLINE`
- [x] `targetType` 目前可能出现：
  - `STANDBY`
  - `PICKUP`
  - `DROPOFF`
  - `CHARGING`

## 四、前端下单页面

- [ ] 决定下单入口形式
- [ ] 方案一：复用现有订单管理页增加“园区下单”
- [ ] 方案二：新增简单 Web 下单页，直接调用 `POST /api/admin/park/orders`
- [ ] 下单页支持选择取货点
- [ ] 下单页支持选择送货点
- [ ] 下单页支持优先级
- [ ] 下单页支持备注
- [ ] 下单成功后能看到订单进入调度
- [ ] 下单失败时展示明确错误信息

## 五、大屏联动展示

- [x] 大屏已有车辆/站点/订单基础接口
- [x] 大屏已可读取车辆是否低电量、是否充电、当前目标点
- [ ] 大屏补齐新的阶段文案和视觉状态
- [ ] 大屏展示订单从创建到完成的完整链路
- [ ] 大屏展示车辆完成订单后回待命或回充电位
- [ ] 大屏区分“执行中 / 充电中 / 待命中”

## 六、状态与数据设计

- [x] 统一车辆运行阶段枚举
- [x] 明确后端内部状态与前端展示字段
- [x] 将充电/低电量/目标点暴露给前端
- [ ] 前端补齐 `runtimeStage` 文案映射，避免展示原始枚举

## 七、联调验收

- [x] 创建订单后可自动分配车辆
- [x] 车辆可前往取货点
- [x] 车辆可前往送货点
- [x] 订单完成后状态可更新
- [x] 电量不足时车辆自动回充
- [x] 充电后车辆可再次接单
- [ ] 大屏轨迹、订单状态、车辆状态三者联动验收
- [ ] 下单页面与大屏联动验收

## 八、当前后端完成范围

- [x] 简化下单接口
- [x] 订单驱动车辆执行
- [x] 送货完成后电量判断
- [x] 低电量自动回充
- [x] 车辆充电状态与低电量状态输出
- [ ] 前端下单页
- [ ] 大屏新状态展示联调

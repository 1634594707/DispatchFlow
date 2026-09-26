package com.fsd.dispatch.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 移动端追踪用的一次聚合读取（路线图 §16.3 的产物）。
 *
 * <p>为什么要有它：手机页每 1.5 s 轮询的是 {@code /park/orders} + {@code /park/vehicles}，
 * 本机预压实测**一次轮询搬 209 KB**，而且读的是整园数据 —— 订单侧是全表读后在内存里排序截断，
 * 车队侧给回 35 台车、每台三条折线（trajectory / geoTrajectory / plannedRouteGeo）。
 * 用户只需要"我这单 + 我这辆车"，其余都是白搬的字节，且延迟随积压线性变差（冷库存 p95 155 ms，
 * 压着 968 条待派单时 1.07 s）。
 *
 * <p>这个响应把范围收到：一条完整订单快照 + 一辆完整车快照 + 最近若干单的**精简**行
 * （精简行只带切换芯片与"是否还在进行"判断要用的字段，不含站点对象与折线）。
 */
@Data
@Builder
public class ParkTrackResponse {

    /** 正在追踪的这一单（完整快照：取送点对象、时间线、阶段）。没有可追踪订单时为 null。 */
    private ParkOrderSnapshotResponse order;

    /** 派给这一单的那台车（完整快照，含它的轨迹与规划线）。未派车或车不可监测时为 null。 */
    private ParkVehicleSnapshotResponse vehicle;

    /** 最近若干单的精简行，只服务页面上的订单切换与"还有单在跑吗"。 */
    private List<RecentOrder> recentOrders;

    /**
     * 该园区还在进行的订单总数。
     *
     * <p>为什么单独给一个数：页头写着"N 单配送中"，而 {@code recentOrders} 是被 limit 截过的列表 ——
     * 用它的长度当计数会在单多的时候**少报**（最多只显示 8）。这条是一个走 idx_status_created_at
     * 的 COUNT，不做全表读；口径按 park_id（不像精简行那样再过一遍站点归属），对现在的数据等价。
     */
    private Long activeCount;

    /**
     * 精简行。刻意不嵌 {@link ParkStationResponse}：前端只用 stationCode/area 判"是不是真实地图的
     * 那一档订单"（{@code isGeoDeliveryStation}），以及用 orderNo 画切换芯片。
     */
    @Data
    @Builder
    public static class RecentOrder {

        private Long orderId;

        private String orderNo;

        private String orderStatus;

        /** 由订单与任务推出来的阶段（不含车队实时阶段）：够用于"是否还在进行"的过滤。 */
        private String runtimeStage;

        private Long vehicleId;

        private String pickupStationCode;

        private String pickupStationArea;

        private String dropoffStationCode;

        private String dropoffStationArea;
    }
}

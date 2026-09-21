package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** MAPF 时空预约与分区配置。M5 */
@Data
@Component
@ConfigurationProperties(prefix = "fsd.mapf")
public class MapfProperties {

    private boolean enabled = true;

    /** 时间桶宽度（毫秒），默认 500ms。 */
    private long bucketMs = 500L;

    /** 每条边预占的时间桶数量。 */
    private int horizonBuckets = 6;

    /** 空间分区网格边长（每轴划分数，4 → 16 zones）。 */
    private int zoneGridSize = 4;

    /** 冲突重规划最大尝试次数。 */
    private int maxReplanAttempts = 4;

    /** 每次冲突对边的额外代价乘数。 */
    private double conflictPenaltyMultiplier = 3.0;

    /**
     * 估算时空预约占位时长用的车辆速度，<b>单位是米/秒</b>。
     *
     * <p>默认 3.66 m/s = <b>13.19 km/h</b>，与 ETA 那个常数同一出处：现役路网 124 条边按边长加权的
     * 调和平均限速（实测，见 §13.17）。
     *
     * <p>这里以前叫 {@code vehicleSpeedPxPerSecond = 8.0}，并且 {@code MapfRoutePlannerService} 会拿
     * {@code fsd.park.vehicle-speed-px-per-second} 覆盖它 —— 那个值是<b>示意坐标上的动画/仿真速度</b>
     * （前端地图与 {@code ParkPilotSimulationServiceImpl.moveVehicle} 在用 px/s），
     * 而它除的分子是 haversine <b>米</b>。两个问题叠在一起：
     * ① 单位不同类，误差还按航向在 0.74–1.23 之间摆；② 8 比 3.66 快 2.19 倍，
     * 于是每条边预约覆盖的时间只有真实占位时间的约 46%（37%–62% 随航向变化），
     * 冲突窗口系统性提前失效 —— MAPF 看着在跑，实际几乎不挡车。
     */
    private double vehicleSpeedMetersPerSecond = 3.66D;
}

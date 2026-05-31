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

    /** 车辆默认速度 px/s，用于估算时间桶。 */
    private double vehicleSpeedPxPerSecond = 8.0;
}

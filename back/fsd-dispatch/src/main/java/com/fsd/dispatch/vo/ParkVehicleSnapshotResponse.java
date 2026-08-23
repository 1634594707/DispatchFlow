package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkVehicleSnapshotResponse {

    private Long parkId;

    private Long vehicleId;

    private String vehicleCode;

    private String vehicleName;

    private String onlineStatus;

    private String dispatchStatus;

    private Long currentTaskId;

    private Long currentOrderId;

    private Integer batteryLevel;

    /** NORMAL / LOW / CRITICAL / CHARGING */
    private String batteryStatus;

    private BigDecimal x;

    private BigDecimal y;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private Double heading;

    private LocalDateTime lastTelemetryAt;

    private Boolean telemetryStale;

    /** 数据年龄（秒）：当前时间 - 最后遥测时间。 */
    private Long telemetryAgeSeconds;

    /** 服务端统一的遥测过期阈值（秒），前端据此展示数据年龄与告警。 */
    private Long telemetryStaleThresholdSeconds;

    private String runtimeStage;

    private String targetCode;

    private String targetType;

    private Boolean charging;

    private Boolean lowBattery;

    private String linkMode;

    private List<ParkPointResponse> trajectory;

    private List<ParkPointResponse> geoTrajectory;

    private List<ParkPointResponse> plannedRouteGeo;

    private String routeSource;

    private Boolean routeInvalid;

    private Boolean manualOverride;
}

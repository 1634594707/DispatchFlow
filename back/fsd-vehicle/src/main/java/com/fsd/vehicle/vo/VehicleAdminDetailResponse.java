package com.fsd.vehicle.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleAdminDetailResponse {

    private Long vehicleId;

    private Long parkId;

    private String vehicleCode;

    private String vehicleName;

    private String vehicleType;

    private String linkMode;

    private String vdaManufacturer;

    private String vdaSerialNumber;

    private String vdaInterfaceName;

    private String onlineStatus;

    private String dispatchStatus;

    private Long currentTaskId;

    private Long currentOrderId;

    private BigDecimal currentLatitude;

    private BigDecimal currentLongitude;

    private Integer batteryLevel;

    private LocalDateTime lastReportTime;

    // ===== 路线执行上下文（路线图 5.2，由管理端 enrichment 填充） =====

    /** 最近一次路线执行的路线 ID（无审计记录为 null）。 */
    private String routeAuditRouteId;

    /** 最近一次路线执行使用的地图版本编码。 */
    private String routeMapVersion;

    /** 最近一次路线执行的偏航距离（米）。 */
    private BigDecimal routeDeviationMeters;

    /** 最近一次路线执行的完成/执行时间。 */
    private LocalDateTime routeExecutedAt;

    /** 当前最近道路节点编码（50m 内匹配；未匹配为 null）。 */
    private String currentRoadNodeCode;

    private String remark;
}

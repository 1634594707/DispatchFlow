package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 站点服务位响应（P0-5 / P1-7 / P1-10）。
 */
@Data
@Builder
public class AdminStationServicePositionResponse {

    private Long id;

    private Long stationId;

    private String stationCode;

    private String stationName;

    private String positionCode;

    private String positionName;

    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private String accessNodeCode;

    /** 服务方向：FORWARD/REVERSE/BIDIRECTIONAL */
    private String serviceDirection;

    /** 允许车辆类型（逗号分隔，NULL=全部） */
    private String allowedVehicleTypes;

    private Integer capacityLimit;

    /** 状态：ACTIVE/OCCUPIED/RESERVED/MAINTENANCE/OUT_OF_SERVICE */
    private String status;

    private Long reservedVehicleId;

    private LocalDateTime reservedUntil;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

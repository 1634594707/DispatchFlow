package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminStationResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String stationCode;

    private String stationName;

    private String stationType;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private String area;

    private String status;

    private Integer sortOrder;

    private Integer capacityLimit;

    /** P0-5: 接入道路节点编码 */
    private String anchorNodeCode;

    /** P0-5: 服务方向（FORWARD/REVERSE/BIDIRECTIONAL） */
    private String serviceDirection;

    /** P1-1: 允许车辆类型（逗号分隔，NULL=全部） */
    private String allowedVehicleTypes;

    /** P1-4: 不可达原因（ROAD_NETWORK_EMPTY/NO_PATH_ON_GRAPH/...） */
    private String unreachableReason;

    /** P1-4: 不可达截止时间（NULL=永久） */
    private LocalDateTime unreachableUntil;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

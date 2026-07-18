package com.fsd.admin.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 站点服务位新增/更新请求（P0-5 / P1-7 / P1-10）。
 */
@Data
public class AdminStationServicePositionUpsertRequest {

    private Long stationId;

    private String positionCode;

    private String positionName;

    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private BigDecimal coordX;

    private BigDecimal coordY;

    /** 接入道路节点编码 */
    private String accessNodeCode;

    /** 服务方向：FORWARD/REVERSE/BIDIRECTIONAL */
    private String serviceDirection;

    /** 允许车辆类型（逗号分隔，NULL=全部） */
    private String allowedVehicleTypes;

    private Integer capacityLimit;

    /** 状态：ACTIVE/MAINTENANCE/OUT_OF_SERVICE */
    private String status;

    private String remark;
}

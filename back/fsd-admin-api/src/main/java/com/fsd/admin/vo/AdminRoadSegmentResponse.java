package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminRoadSegmentResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String fromNodeCode;

    private String toNodeCode;

    private String status;

    private Integer speedLimitKmh;

    private Integer congestionLevel;

    /** P1-1: 道路宽度（米） */
    private BigDecimal widthMeters;

    /** P1-1: 道路等级（ARTERIAL/SECONDARY/SERVICE_ROAD） */
    private String roadClass;

    /** P1-2: 通行语义（DRIVABLE/BLOCKED/PEDESTRIAN_ONLY/SERVICE_ONLY/RESTRICTED/NO_STOP/LOADING_ONLY/CHARGING_ACCESS） */
    private String accessState;

    /** P1-1: 道路几何 GeoJSON LineString */
    private String polylineGeojson;

    /** P1-1: 允许车辆类型（逗号分隔，NULL=全部） */
    private String allowedVehicleTypes;

    /** P1-2: 转向限制（NO_LEFT/NO_RIGHT/NO_U_TURN/STRAIGHT_ONLY） */
    private String turnRestriction;

    /** P1-2: 门禁编码 */
    private String gateCode;

    /** P1-2: 阻塞原因 */
    private String blockReason;

    /** P1-2: 临时封闭开始时间 */
    private LocalDateTime blockedFrom;

    /** P1-2: 临时封闭结束时间 */
    private LocalDateTime blockedUntil;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

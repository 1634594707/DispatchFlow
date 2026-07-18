package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_station")
public class StationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

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

    /** 营业时间窗口，例如 "06:00-22:00" */
    private String serviceHours;

    /** 平均服务时长（秒），用于 ETA 与队列估算 */
    private Integer avgServiceSeconds;

    /** 站点接入的道路节点编码（吸附到 road_node.node_code）；NULL=未配置，需现场核验 */
    private String anchorNodeCode;

    /** 车辆到站服务方向：FORWARD/REVERSE/BIDIRECTIONAL */
    private String serviceDirection;

    /** 允许服务的车辆类型（逗号分隔，NULL=全部） */
    private String allowedVehicleTypes;

    /** 不可达原因：ROAD_CLOSED/NO_SERVICE_POSITION/VEHICLE_TYPE_NOT_ALLOWED/CAPACITY_FULL/MAINTENANCE/OFFLINE/GATE_CLOSED/OUT_OF_RANGE */
    private String unreachableReason;

    /** 不可达失效时间（NULL=永久或正常） */
    private java.time.LocalDateTime unreachableUntil;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    /** 配送区域: GEO_DELIVERY / SCHEMATIC / GENERAL */
    private String deliveryZone;

    private Integer deleted;
}

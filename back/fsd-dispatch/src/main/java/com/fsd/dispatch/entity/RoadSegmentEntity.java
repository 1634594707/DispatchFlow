package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_road_segment")
public class RoadSegmentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String fromNodeCode;

    private String toNodeCode;

    /**
     * 阶段七 7.1：通行方向。
     * BIDIRECTIONAL = 双向通行（默认）；FORWARD = 仅 from→to；REVERSE = 仅 to→from。
     */
    private String direction;

    /**
     * 阶段七 7.4：跨园区连接段的对端园区 ID。
     * NULL 表示园内路段；非 NULL 表示该路段连接 park_id 与 connectingParkId。
     */
    private Long connectingParkId;

    private String status;

    private Integer speedLimitKmh;

    private Integer congestionLevel;

    /** 道路可行驶宽度（米），用于车辆外接矩形碰撞检查 */
    private java.math.BigDecimal widthMeters;

    /** 道路等级：HIGHWAY/ARTERIAL/SECONDARY/SERVICE_ROAD/PEDESTRIAN/FIRE_LANE */
    private String roadClass;

    /** 通行语义：DRIVABLE/PEDESTRIAN_ONLY/SERVICE_ONLY/RESTRICTED/BLOCKED/NO_STOP/LOADING_ONLY/CHARGING_ACCESS */
    private String accessState;

    /** 道路中心线 GeoJSON LineString（GCJ-02），NULL 表示用 from/to 节点连线 */
    private String polylineGeojson;

    /** 允许车辆类型（逗号分隔，NULL=全部） */
    private String allowedVehicleTypes;

    /** 转向限制：NONE/NO_LEFT/NO_RIGHT/NO_U_TURN/NO_STRAIGHT */
    private String turnRestriction;

    /** 关联门禁/闸机/消防通道编码（NULL=无门禁） */
    private String gateCode;

    /** 临时封路原因（施工/事故/活动/消防管控） */
    private String blockReason;

    /** 封路起始时间（NULL=未封路或永久） */
    private LocalDateTime blockedFrom;

    /** 封路结束时间（NULL=永久或未封路） */
    private LocalDateTime blockedUntil;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}

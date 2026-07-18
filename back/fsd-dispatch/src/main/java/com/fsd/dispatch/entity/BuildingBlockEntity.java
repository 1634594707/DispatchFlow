package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 建筑物与障碍物 Polygon 表（V43 / P0-4.2）。
 * 替换 PilotForbiddenZones.BUILDING_BLOCKS 固定近似矩形，
 * 支持真实几何、来源追溯、版本管理、按车辆宽度/朝向膨胀。
 */
@Data
@TableName("t_building_block")
public class BuildingBlockEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String blockCode;

    private String blockName;

    /** 类型：BUILDING/WALL/RIVER/GREENBELT/CONSTRUCTION/PARKING_OBSTACLE/GATEHOUSE */
    private String blockType;

    /** Polygon GeoJSON（GCJ-02 坐标系，外环逆时针） */
    private String polygonGeojson;

    private BigDecimal centroidLng;

    private BigDecimal centroidLat;

    /** 数据来源：OSM/MANUAL/SURVEY/AERIAL */
    private String source;

    private Long mapVersionId;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    /** 是否硬禁行（1=车辆包络不得进入；0=仅警告） */
    private Integer isHardForbidden;

    /** 默认膨胀缓冲（米），按车辆宽度/朝向额外膨胀 */
    private BigDecimal defaultExpansionBufferMeters;

    /** 建筑物高度（米），用于限高检查 */
    private BigDecimal heightMeters;

    private String gateCode;

    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}

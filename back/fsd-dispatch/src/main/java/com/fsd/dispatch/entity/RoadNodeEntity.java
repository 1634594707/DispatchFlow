package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_road_node")
public class RoadNodeEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String nodeCode;

    private BigDecimal coordX;

    private BigDecimal coordY;

    /** 真实 GPS 经度（GCJ-02）。Phase 2 路网校准新增，Phase 4 起 edgeCost 使用此字段计算 haversine 距离。 */
    private BigDecimal coordLng;

    /** 真实 GPS 纬度（GCJ-02）。 */
    private BigDecimal coordLat;

    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}

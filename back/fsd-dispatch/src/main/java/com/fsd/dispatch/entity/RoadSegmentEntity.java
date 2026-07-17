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

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}

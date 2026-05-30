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

    private String status;

    private Integer speedLimitKmh;

    private Integer congestionLevel;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}

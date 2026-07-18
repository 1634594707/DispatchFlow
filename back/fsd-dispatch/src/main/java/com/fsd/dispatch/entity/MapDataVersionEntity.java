package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 地图数据版本表（P2-6）。
 * 能追溯某次调度使用的路网版本，支持审计与回放。
 */
@Data
@TableName("t_map_data_version")
public class MapDataVersionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String versionCode;

    private String versionLabel;

    private Integer roadNodeCount;

    private Integer roadSegmentCount;

    private Integer stationCount;

    private Integer buildingBlockCount;

    private LocalDateTime publishedAt;

    private String publishedBy;

    /** 是否当前激活版本（1=是） */
    private Integer isActive;

    private String checksum;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}

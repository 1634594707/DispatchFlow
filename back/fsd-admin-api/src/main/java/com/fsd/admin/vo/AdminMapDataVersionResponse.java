package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 地图数据版本响应（P2-6）。
 */
@Data
@Builder
public class AdminMapDataVersionResponse {

    private Long id;

    private Long parkId;

    private String parkName;

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
}

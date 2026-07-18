package com.fsd.admin.dto;

import lombok.Data;

/**
 * 地图数据版本新增/更新请求（P2-6）。
 */
@Data
public class AdminMapDataVersionUpsertRequest {

    private Long parkId;

    private String versionCode;

    private String versionLabel;

    private Integer roadNodeCount;

    private Integer roadSegmentCount;

    private Integer stationCount;

    private Integer buildingBlockCount;

    private String publishedBy;

    /** 是否当前激活版本（1=是，0=否） */
    private Integer isActive;

    private String checksum;

    private String remark;
}

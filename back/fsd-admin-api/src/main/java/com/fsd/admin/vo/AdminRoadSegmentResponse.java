package com.fsd.admin.vo;

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

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

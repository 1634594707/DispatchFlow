package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminParkResponse {

    private Long id;

    private String parkCode;

    private String parkName;

    private Integer mapWidth;

    private Integer mapHeight;

    private Integer minZoom;

    private Integer maxZoom;

    private BigDecimal vehicleSpeedPxPerSecond;

    private String status;

    private boolean defaultPark;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

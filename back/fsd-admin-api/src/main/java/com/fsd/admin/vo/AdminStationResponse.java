package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminStationResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String stationCode;

    private String stationName;

    private String stationType;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private String area;

    private String status;

    private Integer sortOrder;

    private Integer capacityLimit;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

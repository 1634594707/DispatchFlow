package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminGeofenceResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String fenceCode;

    private String fenceName;

    private String fenceType;

    private List<List<BigDecimal>> polygon;

    private String status;

    private String remark;

    private LocalDateTime updatedAt;
}

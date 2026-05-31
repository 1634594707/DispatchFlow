package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkGeofenceResponse {

    private Long id;

    private Long parkId;

    private String fenceCode;

    private String fenceName;

    private String fenceType;

    /** GCJ-02 polygon vertices [lng, lat] */
    private List<List<BigDecimal>> polygon;

    private String status;

    private String remark;

    private LocalDateTime updatedAt;
}

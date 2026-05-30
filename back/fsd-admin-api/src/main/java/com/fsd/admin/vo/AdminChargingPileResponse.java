package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminChargingPileResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String pileCode;

    private String pileName;

    private Long parkingSlotId;

    private String parkingSlotCode;

    private String status;

    private Long occupiedVehicleId;

    private BigDecimal maxPowerKw;

    private Integer sortOrder;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

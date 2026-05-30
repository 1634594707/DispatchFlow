package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminParkingSlotResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String slotCode;

    private String slotName;

    private String slotType;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private String status;

    private Long occupiedVehicleId;

    private Integer sortOrder;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminBatterySwapCabinetResponse {

    private Long id;

    private Long parkId;

    private String parkName;

    private String cabinetCode;

    private String cabinetName;

    private BigDecimal coordX;

    private BigDecimal coordY;

    private Integer slotCount;

    private String status;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

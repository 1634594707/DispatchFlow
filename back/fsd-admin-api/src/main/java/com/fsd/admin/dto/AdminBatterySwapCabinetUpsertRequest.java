package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminBatterySwapCabinetUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "换电柜编码不能为空")
    @Size(max = 64, message = "换电柜编码不能超过 64 位")
    private String cabinetCode;

    @NotBlank(message = "换电柜名称不能为空")
    @Size(max = 128, message = "换电柜名称不能超过 128 位")
    private String cabinetName;

    @NotNull(message = "坐标 X 不能为空")
    private BigDecimal coordX;

    @NotNull(message = "坐标 Y 不能为空")
    private BigDecimal coordY;

    private Integer slotCount;

    private String status;

    private String remark;
}

package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminChargingPileUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "充电桩编码不能为空")
    @Size(max = 64, message = "充电桩编码不能超过 64 位")
    private String pileCode;

    @NotBlank(message = "充电桩名称不能为空")
    @Size(max = 128, message = "充电桩名称不能超过 128 位")
    private String pileName;

    @NotNull(message = "绑定车位 ID 不能为空")
    private Long parkingSlotId;

    private String status;

    private BigDecimal maxPowerKw;

    private Integer sortOrder;

    @Size(max = 255, message = "备注不能超过 255 位")
    private String remark;
}

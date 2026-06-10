package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminParkingSlotUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "车位编码不能为空")
    @Size(max = 64, message = "车位编码不能超过 64 位")
    private String slotCode;

    @NotBlank(message = "车位名称不能为空")
    @Size(max = 128, message = "车位名称不能超过 128 位")
    private String slotName;

    private String slotType;

    @NotNull(message = "坐标 X 不能为空")
    private BigDecimal coordX;

    @NotNull(message = "坐标 Y 不能为空")
    private BigDecimal coordY;

    private String status;

    private Integer sortOrder;

    @Size(max = 255, message = "备注不能超过 255 位")
    private String remark;
}

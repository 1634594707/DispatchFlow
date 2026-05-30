package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminRoadNodeUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "节点编码不能为空")
    @Size(max = 64, message = "节点编码不能超过 64 位")
    private String nodeCode;

    @NotNull(message = "坐标 X 不能为空")
    private BigDecimal coordX;

    @NotNull(message = "坐标 Y 不能为空")
    private BigDecimal coordY;

    private String status;

    @Size(max = 255, message = "备注不能超过 255 位")
    private String remark;
}

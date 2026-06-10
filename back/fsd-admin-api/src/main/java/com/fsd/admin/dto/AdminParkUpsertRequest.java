package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminParkUpsertRequest {

    @NotBlank(message = "园区编码不能为空")
    @Size(max = 64, message = "园区编码不能超过 64 位")
    private String parkCode;

    @NotBlank(message = "园区名称不能为空")
    @Size(max = 128, message = "园区名称不能超过 128 位")
    private String parkName;

    private Integer mapWidth;

    private Integer mapHeight;

    private Integer minZoom;

    private Integer maxZoom;

    private BigDecimal vehicleSpeedPxPerSecond;

    private String status;

    private Boolean defaultPark;

    @Size(max = 255, message = "备注不能超过 255 位")
    private String remark;
}

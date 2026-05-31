package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminGeofenceUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "围栏编码不能为空")
    @Size(max = 64, message = "围栏编码不能超过 64 位")
    private String fenceCode;

    @NotBlank(message = "围栏名称不能为空")
    @Size(max = 128, message = "围栏名称不能超过 128 位")
    private String fenceName;

    @NotBlank(message = "围栏类型不能为空")
    private String fenceType;

    @NotBlank(message = "多边形坐标不能为空")
    private String polygonJson;

    private String status;

    @Size(max = 255, message = "备注不能超过 255 位")
    private String remark;
}

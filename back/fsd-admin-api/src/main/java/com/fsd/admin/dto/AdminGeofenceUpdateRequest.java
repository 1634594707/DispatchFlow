package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminGeofenceUpdateRequest {

    @Size(max = 128, message = "围栏名称不能超过 128 位")
    private String fenceName;

    private String fenceType;

    private String polygonJson;

    private String status;

    @Size(max = 255, message = "备注不能超过 255 位")
    private String remark;
}

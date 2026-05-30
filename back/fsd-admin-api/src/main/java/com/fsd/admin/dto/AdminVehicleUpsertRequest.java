package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminVehicleUpsertRequest {

    @NotBlank(message = "车辆编码不能为空")
    @Size(max = 64)
    private String vehicleCode;

    @NotBlank(message = "车辆名称不能为空")
    @Size(max = 128)
    private String vehicleName;

    private String vehicleType;

    private String linkMode;

    @Size(max = 255)
    private String remark;
}

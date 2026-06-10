package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminVehicleMaintenanceUpsertRequest {

    @NotNull(message = "车辆 ID 不能为空")
    private Long vehicleId;

    @NotBlank(message = "维护类型不能为空")
    private String maintenanceType;

    @NotBlank(message = "维护描述不能为空")
    @Size(max = 512)
    private String description;

    @NotNull(message = "维护时间不能为空")
    private LocalDateTime maintenanceAt;

    private String status;

    @Size(max = 255)
    private String remark;
}

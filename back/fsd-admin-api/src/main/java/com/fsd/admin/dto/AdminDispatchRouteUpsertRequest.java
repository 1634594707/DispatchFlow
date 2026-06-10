package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;
import lombok.Data;

@Data
public class AdminDispatchRouteUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "线路编码不能为空")
    @Size(max = 64)
    private String routeCode;

    @NotBlank(message = "线路名称不能为空")
    @Size(max = 128)
    private String routeName;

    private String status;

    private LocalTime serviceStartTime;

    private LocalTime serviceEndTime;

    private String requiredVehicleType;

    private Integer maxConcurrentTasks;

    @NotNull(message = "站点顺序不能为空")
    private List<Long> stationIds;

    private String remark;
}

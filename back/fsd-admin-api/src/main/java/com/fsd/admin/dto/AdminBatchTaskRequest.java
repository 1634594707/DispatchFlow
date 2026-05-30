package com.fsd.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class AdminBatchTaskRequest {

    @NotEmpty(message = "任务 ID 列表不能为空")
    private List<Long> taskIds;

    private Long vehicleId;

    private String remark;
}

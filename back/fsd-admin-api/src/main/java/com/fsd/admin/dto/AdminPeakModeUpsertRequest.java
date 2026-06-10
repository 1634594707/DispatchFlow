package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminPeakModeUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "模式不能为空")
    private String mode;

    private String templateCode;

    private String scheduleCron;

    private String scheduleEndCron;
}

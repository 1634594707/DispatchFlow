package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminPeakModeResponse {

    private Long parkId;

    private String mode;

    private String templateCode;

    private String scheduleCron;

    private String scheduleEndCron;

    private LocalDateTime enabledAt;

    private LocalDateTime updatedAt;
}

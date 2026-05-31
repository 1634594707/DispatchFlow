package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminReportScheduleResponse {

    private Long id;

    private Long parkId;

    private String cronExpression;

    private String recipients;

    private Boolean enabled;

    private LocalDateTime lastSentAt;
}

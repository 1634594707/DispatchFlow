package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTrajectoryDwellResponse {

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private double x;

    private double y;

    private long durationMinutes;
}

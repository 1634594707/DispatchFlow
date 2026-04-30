package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchTaskAssignResponse {

    private Long taskId;

    private String status;

    private Long vehicleId;

    private String message;

    private LocalDateTime assignTime;
}

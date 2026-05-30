package com.fsd.admin.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class AdminOperateLogQueryRequest {

    private Long taskId;

    private Long vehicleId;

    private String operateType;

    private String operatorName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer pageNo = 1;

    private Integer pageSize = 20;
}

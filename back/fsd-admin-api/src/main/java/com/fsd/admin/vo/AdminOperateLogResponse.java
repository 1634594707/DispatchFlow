package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminOperateLogResponse {

    private Long id;

    private Long taskId;

    private String taskNo;

    private Long vehicleId;

    private String operateType;

    private String beforeStatus;

    private String afterStatus;

    private String operatorType;

    private String operatorId;

    private String operatorName;

    private String operateRemark;

    private LocalDateTime createdAt;
}

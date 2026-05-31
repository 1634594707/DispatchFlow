package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminFieldOpsTicketResponse {

    private Long id;

    private Long exceptionId;

    private Long assigneeUserId;

    private String assigneeName;

    private String status;

    private String notes;

    private String exceptionType;

    private String exceptionMsg;

    private LocalDateTime createdAt;
}

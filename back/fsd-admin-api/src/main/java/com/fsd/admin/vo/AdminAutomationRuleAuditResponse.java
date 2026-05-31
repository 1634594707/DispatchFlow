package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAutomationRuleAuditResponse {

    private Long id;

    private Long ruleId;

    private String action;

    private String operator;

    private String detail;

    private LocalDateTime createdAt;
}

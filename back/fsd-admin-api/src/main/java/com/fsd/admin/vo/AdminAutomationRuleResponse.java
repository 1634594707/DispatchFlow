package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminAutomationRuleResponse {

    private Long id;

    private Long parkId;

    private String ruleName;

    private String conditionType;

    private String conditionValue;

    private String actionType;

    private String actionParamsJson;

    private boolean enabled;

    private LocalDateTime updatedAt;
}

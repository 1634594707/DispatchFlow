package com.fsd.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminAutomationRuleUpsertRequest {

    @NotNull(message = "园区 ID 不能为空")
    private Long parkId;

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128)
    private String ruleName;

    @NotBlank(message = "条件类型不能为空")
    private String conditionType;

    @NotBlank(message = "条件值不能为空")
    private String conditionValue;

    @NotBlank(message = "动作类型不能为空")
    private String actionType;

    private String actionParamsJson;

    private Boolean enabled;
}

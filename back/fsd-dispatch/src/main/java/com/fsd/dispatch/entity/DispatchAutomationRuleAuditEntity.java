package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_automation_rule_audit")
public class DispatchAutomationRuleAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ruleId;

    private String action;

    private String operator;

    private String detail;

    private LocalDateTime createdAt;
}

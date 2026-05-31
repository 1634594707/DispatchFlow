package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_automation_rule")
public class DispatchAutomationRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parkId;

    private String ruleName;

    private String conditionType;

    private String conditionValue;

    private String actionType;

    private String actionParamsJson;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}

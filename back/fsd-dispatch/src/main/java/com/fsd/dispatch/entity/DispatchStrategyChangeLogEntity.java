package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_strategy_change_log")
public class DispatchStrategyChangeLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long profileId;

    private String profileName;

    private String changeType;

    private String operatorName;

    private String changeSummary;

    private LocalDateTime createdAt;
}

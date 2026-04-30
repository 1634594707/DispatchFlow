package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_task_operate_log")
public class DispatchTaskOperateLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String operateType;

    private String beforeStatus;

    private String afterStatus;

    private String operatorType;

    private String operatorId;

    private String operatorName;

    private String operateRemark;

    private LocalDateTime createdAt;
}

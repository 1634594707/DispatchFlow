package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_task")
public class DispatchTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;

    private Long orderId;

    private Long vehicleId;

    private String dispatchType;

    private String status;

    private String failReasonCode;

    private String failReasonMsg;

    private LocalDateTime assignTime;

    private LocalDateTime startTime;

    private LocalDateTime finishTime;

    private Integer manualFlag;

    private Integer retryCount;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}

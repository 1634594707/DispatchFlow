package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_exception_record")
public class DispatchExceptionRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long orderId;

    private Long vehicleId;

    private String exceptionType;

    private String exceptionStatus;

    private String exceptionMsg;

    private LocalDateTime occurTime;

    private LocalDateTime resolvedTime;

    private String resolverId;

    private String resolveRemark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

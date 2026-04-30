package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_event_outbox")
public class DispatchEventOutboxEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String eventType;

    private String businessKey;

    private String payload;

    private String status;

    private Integer retryCount;

    private String lastError;

    private LocalDateTime nextRetryTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_webhook_delivery_log")
public class WebhookDeliveryLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long subscriptionId;

    private String eventType;

    private String businessKey;

    private Integer httpStatus;

    private Integer success;

    private Integer attemptNo;

    private String payloadSummary;

    private String errorMessage;

    private LocalDateTime deliveredAt;
}

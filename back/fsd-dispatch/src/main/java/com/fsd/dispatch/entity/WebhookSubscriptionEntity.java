package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_webhook_subscription")
public class WebhookSubscriptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String callbackUrl;

    private String secretToken;

    private String eventTypes;

    private Integer enabled;

    private Integer failureCount;

    private LocalDateTime lastDeliveryAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}

package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_external_api_key")
public class ExternalApiKeyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyName;

    private String apiKey;

    private String status;

    private Integer rateLimitPerMinute;

    private Long totalCalls;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}

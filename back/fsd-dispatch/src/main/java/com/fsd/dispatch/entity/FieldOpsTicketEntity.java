package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_field_ops_ticket")
public class FieldOpsTicketEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long exceptionId;

    private Long assigneeUserId;

    private String status;

    private String notes;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

package com.fsd.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_admin_session")
public class AdminSessionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String token;

    private Long userId;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;
}

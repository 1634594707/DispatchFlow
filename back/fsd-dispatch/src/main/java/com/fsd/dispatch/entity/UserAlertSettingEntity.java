package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_user_alert_setting")
public class UserAlertSettingEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String rulesJson;

    private LocalDateTime updatedAt;
}

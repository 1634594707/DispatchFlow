package com.fsd.dispatch.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_dispatch_strategy_profile")
public class DispatchStrategyProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String profileName;

    private String profileType;

    private Integer activeFlag;

    private Integer grayPercent;

    private Long parkId;

    private BigDecimal weightDistance;

    private BigDecimal weightSocMargin;

    private BigDecimal weightPluggedStandbyBonus;

    private Integer minAssignableSoc;

    private Integer fullSoc;

    private String energyRecoveryMode;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer version;

    private Integer deleted;
}

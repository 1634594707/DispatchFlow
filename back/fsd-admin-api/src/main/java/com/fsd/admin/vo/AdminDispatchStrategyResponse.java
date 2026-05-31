package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDispatchStrategyResponse {

    private Long id;

    private String profileName;

    private String profileType;

    private boolean active;

    private Integer grayPercent;

    private Long parkId;

    private BigDecimal weightDistance;

    private BigDecimal weightSocMargin;

    private BigDecimal weightPluggedStandbyBonus;

    private Integer minAssignableSoc;

    private Integer fullSoc;

    private String energyRecoveryMode;

    private String remark;

    private LocalDateTime updatedAt;
}

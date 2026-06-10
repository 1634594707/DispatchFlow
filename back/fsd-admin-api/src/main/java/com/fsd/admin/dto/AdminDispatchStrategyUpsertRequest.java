package com.fsd.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AdminDispatchStrategyUpsertRequest {

    @NotBlank
    private String profileName;

    @NotBlank
    private String profileType;

    @Min(0)
    @Max(100)
    private Integer grayPercent;

    private Long parkId;

    @NotNull
    private BigDecimal weightDistance;

    @NotNull
    private BigDecimal weightSocMargin;

    @NotNull
    private BigDecimal weightPluggedStandbyBonus;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer minAssignableSoc;

    @NotNull
    @Min(0)
    @Max(100)
    private Integer fullSoc;

    private String remark;

    private String energyRecoveryMode;
}

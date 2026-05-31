package com.fsd.dispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class VehicleTelemetryRequest {

    @NotBlank(message = "vehicleCode is required")
    private String vehicleCode;

    @NotBlank(message = "runtimeStage is required")
    private String runtimeStage;

    private Boolean pluggedIn;

    private String targetCode;

    private String targetType;

    @NotNull(message = "soc is required")
    private Integer soc;

    @NotNull(message = "x is required")
    private BigDecimal x;

    @NotNull(message = "y is required")
    private BigDecimal y;

    /** Optional GCJ-02 longitude from on-vehicle GPS; falls back to park x/y transform. */
    private BigDecimal longitude;

    /** Optional GCJ-02 latitude from on-vehicle GPS; falls back to park x/y transform. */
    private BigDecimal latitude;

    @NotNull(message = "reportTime is required")
    private LocalDateTime reportTime;

    @NotNull(message = "eventSeq is required")
    private Long eventSeq;
}

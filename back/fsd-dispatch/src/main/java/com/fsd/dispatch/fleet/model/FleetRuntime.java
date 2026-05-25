package com.fsd.dispatch.fleet.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetRuntime {

    private Long vehicleId;

    private String runtimeStage;

    private Boolean pluggedIn;

    private String targetCode;

    private String targetType;

    private Integer soc;

    private Long parkId;

    private BigDecimal x;

    private BigDecimal y;

    private LocalDateTime lastTelemetryAt;

    @Builder.Default
    private List<FleetTrajectoryPoint> trajectory = new ArrayList<>();
}

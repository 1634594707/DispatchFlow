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

    /** GCJ-02 longitude cached from telemetry or park x/y transform. */
    private BigDecimal longitude;

    /** GCJ-02 latitude cached from telemetry or park x/y transform. */
    private BigDecimal latitude;

    /** 车辆朝向（度，高德 Marker angle）。 */
    private Double heading;

    private LocalDateTime lastTelemetryAt;

    @Builder.Default
    private List<FleetTrajectoryPoint> trajectory = new ArrayList<>();

    @Builder.Default
    private List<FleetTrajectoryPoint> geoTrajectory = new ArrayList<>();

    @Builder.Default
    private List<FleetTrajectoryPoint> plannedRouteGeo = new ArrayList<>();

    private String routeSource;

    private Boolean routeInvalid;
}

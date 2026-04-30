package com.fsd.dispatch.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsd.park")
public class ParkPilotProperties {

    private boolean enabled = true;

    private Integer width = 1200;

    private Integer height = 800;

    private Integer minZoom = -1;

    private Integer maxZoom = 3;

    private BigDecimal vehicleSpeedPxPerSecond = new BigDecimal("8");

    private String xFieldAlias = "currentLongitude";

    private String yFieldAlias = "currentLatitude";

    private List<StationConfig> stations = new ArrayList<>();

    private List<PointConfig> parkingSpots = new ArrayList<>();

    private List<RoadNodeConfig> roadNodes = new ArrayList<>();

    private List<RoadSegmentConfig> roadSegments = new ArrayList<>();

    private SimulationConfig simulation = new SimulationConfig();

    @Data
    public static class StationConfig {

        private Long id;

        private String code;

        private String name;

        private BigDecimal x;

        private BigDecimal y;

        private String area;
    }

    @Data
    public static class PointConfig {

        private String code;

        private BigDecimal x;

        private BigDecimal y;
    }

    @Data
    public static class SimulationConfig {

        private boolean enabled = true;

        private int vehicleCount = 4;

        private int maxTrailSize = 30;

        private int offlineDurationSeconds = 8;

        private double offlineProbability = 0.02D;

        private int lowBatteryThreshold = 20;
    }

    @Data
    public static class RoadNodeConfig {

        private String code;

        private BigDecimal x;

        private BigDecimal y;
    }

    @Data
    public static class RoadSegmentConfig {

        private String from;

        private String to;
    }
}

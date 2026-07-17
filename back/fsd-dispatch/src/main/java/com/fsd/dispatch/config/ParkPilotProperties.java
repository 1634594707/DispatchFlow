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

    private String defaultParkCode = "DEFAULT";

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

    private GeoConfig geo = new GeoConfig();

    @Data
    public static class GeoConfig {

        /** Map park schematic x/y to GCJ-02 around 叠石桥家纺产业带. */
        private boolean enabled = true;

        private BigDecimal anchorLng = new BigDecimal("121.080354");

        private BigDecimal anchorLat = new BigDecimal("31.961977");

        private Integer parkWidthMeters = 1570;

        private Integer parkHeightMeters = 470;

        private String scenario = "ZJF_DIESHIQIAO_PILOT";

        /** Path to pilot_osm_geo.json; empty = auto-detect data/pilot_osm_geo.json */
        private String osmGeoPath = "";
    }

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

        private int vehicleCount = 3;

        /** 叠石桥真实地图仿真车数量（ZJF-AV-*，与 PARK-* 分池）。 */
        private int geoVehicleCount = 3;

        private int maxTrailSize = 30;

        private int offlineDurationSeconds = 8;

        private double offlineProbability = 0.005D;

        /** SOC at or below this value is treated as low battery (must charge). */
        private int lowBatteryThreshold = 25;

        /** Vehicles below this SOC are excluded from auto-assign. */
        private int minAssignableBattery = 30;

        /** Target SOC before leaving the charging pile. */
        private int fullChargeLevel = 100;

        /** Battery points recovered per simulation tick while CHARGING. */
        private int chargeRatePerTick = 4;

        /** Minimum displayed SOC while discharging. */
        private int reserveBatteryFloor = 8;

        /** Busy drain: subtract 1% every N movement ticks (1 = every tick). */
        private int busyDrainIntervalTicks = 4;

        /** Idle drain probability per tick (0-1). */
        private double idleDrainProbability = 0.06D;

        /** TO_PICKUP 阶段最大时长（秒），超过后自动转入 EMERGENCY_PARKING。 */
        private int toPickupTimeoutSeconds = 300;
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

package com.fsd.dispatch.geo;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.GeoCalibrationPointResponse;
import com.fsd.dispatch.vo.GeoTransformResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * GCJ-02 ↔ park schematic x/y transform for geo map overlay.
 * Calibration points are derived from park stations (≥3 control points).
 */
@Service
public class CoordinateTransformService {

    private static final double METERS_PER_DEGREE_LAT = 111_320d;

    private final ParkPilotProperties parkPilotProperties;
    private final ParkGeoTransformService parkGeoTransformService;
    private final ParkStationService parkStationService;

    public CoordinateTransformService(ParkPilotProperties parkPilotProperties,
                                      ParkGeoTransformService parkGeoTransformService,
                                      ParkStationService parkStationService) {
        this.parkPilotProperties = parkPilotProperties;
        this.parkGeoTransformService = parkGeoTransformService;
        this.parkStationService = parkStationService;
    }

    public boolean isEnabled() {
        return parkGeoTransformService.isEnabled();
    }

    public Optional<GeoTransformResponse> parkToGcj02(BigDecimal parkX, BigDecimal parkY) {
        return parkGeoTransformService.toGcj02(parkX, parkY)
                .map(point -> GeoTransformResponse.builder()
                        .parkX(parkX)
                        .parkY(parkY)
                        .longitude(point.longitude())
                        .latitude(point.latitude())
                        .build());
    }

    public Optional<GeoTransformResponse> gcj02ToPark(BigDecimal longitude, BigDecimal latitude) {
        if (!isEnabled() || longitude == null || latitude == null) {
            return Optional.empty();
        }
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        int mapWidth = safeDimension(parkPilotProperties.getWidth(), 1200);
        int mapHeight = safeDimension(parkPilotProperties.getHeight(), 800);
        int widthMeters = safeDimension(geo.getParkWidthMeters(), 2400);
        int heightMeters = safeDimension(geo.getParkHeightMeters(), 1600);

        double anchorLng = geo.getAnchorLng().doubleValue();
        double anchorLat = geo.getAnchorLat().doubleValue();
        double metersPerPxX = widthMeters / (double) mapWidth;
        double metersPerPxY = heightMeters / (double) mapHeight;
        double deltaEastMeters = (longitude.doubleValue() - anchorLng) * metersPerDegreeLng(anchorLat);
        double deltaNorthMeters = (latitude.doubleValue() - anchorLat) * METERS_PER_DEGREE_LAT;
        double parkX = deltaEastMeters / metersPerPxX + mapWidth / 2d;
        double parkY = mapHeight / 2d - deltaNorthMeters / metersPerPxY;
        return Optional.of(GeoTransformResponse.builder()
                .parkX(scale(parkX))
                .parkY(scale(parkY))
                .longitude(longitude)
                .latitude(latitude)
                .build());
    }

    public List<GeoCalibrationPointResponse> listCalibrationPoints(Long parkId) {
        Long effectiveParkId = parkId != null ? parkId : parkStationService.requireDefaultPark().getId();
        return parkStationService.listStations(effectiveParkId).stream()
                .map(this::toCalibrationPoint)
                .filter(point -> point.getLongitude() != null && point.getLatitude() != null)
                .toList();
    }

    private GeoCalibrationPointResponse toCalibrationPoint(ParkStationResponse station) {
        var geo = parkGeoTransformService.toGcj02(station.getX(), station.getY()).orElse(null);
        return GeoCalibrationPointResponse.builder()
                .code(station.getStationCode())
                .name(station.getStationName())
                .parkX(station.getX())
                .parkY(station.getY())
                .longitude(geo != null ? geo.longitude() : null)
                .latitude(geo != null ? geo.latitude() : null)
                .build();
    }

    private static int safeDimension(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private static double metersPerDegreeLng(double latitudeDegrees) {
        return METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(latitudeDegrees));
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}

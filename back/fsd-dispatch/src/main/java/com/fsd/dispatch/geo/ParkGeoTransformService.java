package com.fsd.dispatch.geo;

import com.fsd.dispatch.config.ParkPilotProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ParkGeoTransformService {

    private static final double METERS_PER_DEGREE_LAT = 111_320d;

    private final ParkPilotProperties parkPilotProperties;

    public ParkGeoTransformService(ParkPilotProperties parkPilotProperties) {
        this.parkPilotProperties = parkPilotProperties;
    }

    public boolean isEnabled() {
        ParkPilotProperties.GeoConfig geo = parkPilotProperties.getGeo();
        return geo != null && geo.isEnabled();
    }

    public Optional<GeoPoint> toGcj02(BigDecimal parkX, BigDecimal parkY) {
        if (!isEnabled() || parkX == null || parkY == null) {
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
        double deltaEastMeters = (parkX.doubleValue() - mapWidth / 2d) * metersPerPxX;
        double deltaNorthMeters = (mapHeight / 2d - parkY.doubleValue()) * metersPerPxY;
        double lng = anchorLng + deltaEastMeters / metersPerDegreeLng(anchorLat);
        double lat = anchorLat + deltaNorthMeters / METERS_PER_DEGREE_LAT;
        return Optional.of(new GeoPoint(scale(lng), scale(lat)));
    }

    private static int safeDimension(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private double metersPerDegreeLng(double latitudeDegrees) {
        return METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(latitudeDegrees));
    }

    /** GCJ-02 → 园区 schematic x/y（与 {@link #toGcj02} 互逆）。 */
    public Optional<ParkPoint> fromGcj02(BigDecimal longitude, BigDecimal latitude) {
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
        double parkX = mapWidth / 2d + deltaEastMeters / metersPerPxX;
        double parkY = mapHeight / 2d - deltaNorthMeters / metersPerPxY;
        return Optional.of(new ParkPoint(scale(parkX), scale(parkY)));
    }

    private static BigDecimal scale(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }

    public record GeoPoint(BigDecimal longitude, BigDecimal latitude) {
    }

    public record ParkPoint(BigDecimal x, BigDecimal y) {
    }
}

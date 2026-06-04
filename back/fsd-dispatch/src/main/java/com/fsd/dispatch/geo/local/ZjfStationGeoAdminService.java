package com.fsd.dispatch.geo.local;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 管理端保存 ZJF 试点站点时的道路吸附与坐标校验（V4-S4）。
 */
@Service
public class ZjfStationGeoAdminService {

    private final StationRoadSnapService stationRoadSnapService;
    private final StationCoordinateValidator stationCoordinateValidator;

    public ZjfStationGeoAdminService(StationRoadSnapService stationRoadSnapService,
                                     StationCoordinateValidator stationCoordinateValidator) {
        this.stationRoadSnapService = stationRoadSnapService;
        this.stationCoordinateValidator = stationCoordinateValidator;
    }

    public void snapAndValidate(StationEntity station) {
        if (!isZjfPilotStation(station)) {
            return;
        }
        if (station.getCoordLng() == null || station.getCoordLat() == null) {
            return;
        }
        double lng = station.getCoordLng().doubleValue();
        double lat = station.getCoordLat().doubleValue();
        Optional<GeoPoint> snapped = stationRoadSnapService.snapToNearestRoad(lng, lat);
        if (snapped.isPresent()) {
            GeoPoint point = snapped.get();
            station.setCoordLng(point.longitude());
            station.setCoordLat(point.latitude());
        }
        StationCoordinateValidator.ValidationResult result = stationCoordinateValidator.validate(
                station.getCoordLng().doubleValue(),
                station.getCoordLat().doubleValue());
        if (!result.valid()) {
            throw new BusinessException("STATION_COORD_INVALID", result.summary());
        }
    }

    private static boolean isZjfPilotStation(StationEntity station) {
        if ("ZJF".equals(station.getArea())) {
            return true;
        }
        String code = station.getStationCode();
        return code != null && code.startsWith("ZJF-");
    }
}

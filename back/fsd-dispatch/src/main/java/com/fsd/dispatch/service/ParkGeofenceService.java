package com.fsd.dispatch.service;

import com.fsd.dispatch.vo.ParkGeofenceResponse;
import java.math.BigDecimal;
import java.util.List;

public interface ParkGeofenceService {

    List<ParkGeofenceResponse> listByPark(Long parkId);

    List<ParkGeofenceResponse> listActiveByPark(Long parkId);

    ParkGeofenceResponse requireById(Long geofenceId);

    ParkGeofenceResponse create(Long parkId,
                                String fenceCode,
                                String fenceName,
                                String fenceType,
                                String polygonJson,
                                String remark);

    ParkGeofenceResponse update(Long geofenceId,
                                String fenceName,
                                String fenceType,
                                String polygonJson,
                                String status,
                                String remark);

    void delete(Long geofenceId);

    boolean isInsideFence(String fenceType, String polygonJson, BigDecimal longitude, BigDecimal latitude);
}

package com.fsd.dispatch.service;

import java.math.BigDecimal;
import java.util.List;

public interface TrafficZoneControlService {

    record PauseZone(double minX, double minY, double maxX, double maxY, String label) {
    }

    List<PauseZone> listPauseZones(Long parkId);

    PauseZone addPauseZone(Long parkId, double minX, double minY, double maxX, double maxY, String label);

    void clearPauseZones(Long parkId);

    boolean isPointInPausedZone(Long parkId, BigDecimal x, BigDecimal y);
}

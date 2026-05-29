package com.fsd.dispatch.service;

import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.List;

public interface ParkRoutePlannerService {

    List<ParkPointResponse> buildRoute(Long parkId, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY);

    /**
     * Returns false when start/end cannot be connected on the park road graph (P2-11).
     */
    boolean isReachable(Long parkId, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY);
}

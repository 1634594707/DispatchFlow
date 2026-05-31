package com.fsd.dispatch.service;

import com.fsd.dispatch.road.ParkRoadGraph;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface ParkRoutePlannerService {

    List<ParkPointResponse> buildRoute(Long parkId, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY);

    /**
     * Returns false when start/end cannot be connected on the park road graph (P2-11).
     */
    boolean isReachable(Long parkId, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY);

    ParkRoadGraph loadGraph(Long parkId);

    List<String> shortestNodePathWithPenalties(ParkRoadGraph graph,
                                               BigDecimal startX, BigDecimal startY,
                                               BigDecimal endX, BigDecimal endY,
                                               Map<String, Double> edgePenalties);

    List<ParkPointResponse> buildRouteFromNodePath(ParkRoadGraph graph,
                                                   BigDecimal startX, BigDecimal startY,
                                                   BigDecimal endX, BigDecimal endY,
                                                   List<String> nodePath);
}

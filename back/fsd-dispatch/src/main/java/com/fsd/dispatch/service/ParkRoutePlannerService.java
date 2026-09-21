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

    /**
     * 当前使用中的路网图版本指纹，供决策快照记录"这一单看到的是哪一版路网"。
     * 无缓存实现返回 null。
     */
    default String graphVersion(Long parkId) {
        return null;
    }

    /** 丢弃缓存，下一次规划重新从库里全量建图。围栏/路网变更后调用可立即生效。 */
    default void invalidateGraphCache() {
    }

    List<String> shortestNodePathWithPenalties(ParkRoadGraph graph,
                                               BigDecimal startX, BigDecimal startY,
                                               BigDecimal endX, BigDecimal endY,
                                               Map<String, Double> edgePenalties);

    List<ParkPointResponse> buildRouteFromNodePath(ParkRoadGraph graph,
                                                   BigDecimal startX, BigDecimal startY,
                                                   BigDecimal endX, BigDecimal endY,
                                                   List<String> nodePath);
}

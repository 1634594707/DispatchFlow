package com.fsd.admin.service;

import com.fsd.admin.vo.RoadRouteHealthResponse;
import com.fsd.dispatch.config.AmapProperties;
import com.fsd.dispatch.geo.amap.AmapRoadRouteService;
import com.fsd.dispatch.geo.local.LocalPilotRoadGraphService;
import org.springframework.stereotype.Service;

@Service
public class RoadRouteHealthAdminService {

    private final AmapRoadRouteService amapRoadRouteService;
    private final LocalPilotRoadGraphService localPilotRoadGraphService;
    private final AmapProperties amapProperties;

    public RoadRouteHealthAdminService(AmapRoadRouteService amapRoadRouteService,
                                       LocalPilotRoadGraphService localPilotRoadGraphService,
                                       AmapProperties amapProperties) {
        this.amapRoadRouteService = amapRoadRouteService;
        this.localPilotRoadGraphService = localPilotRoadGraphService;
        this.amapProperties = amapProperties;
    }

    public RoadRouteHealthResponse getHealth() {
        boolean amapDriving = amapRoadRouteService.isAvailable();
        boolean localGraph = localPilotRoadGraphService.isAvailable();
        long amapSuccessCount = amapRoadRouteService.getAmapSuccessCount();
        long fallbackCount = amapRoadRouteService.getFallbackCount();
        int localGraphSegments = localPilotRoadGraphService.segmentCount();

        StringBuilder detail = new StringBuilder();
        if (!amapDriving && !localGraph) {
            detail.append("NO_ROUTE_AVAILABLE: 请配置 FSD_AMAP_WEB_SERVICE_KEY。");
        } else if (amapDriving) {
            detail.append("AMAP_OK");
        } else if (localGraph) {
            detail.append("LOCAL_GRAPH_MODE: 使用试点本地路网兜底（无高德 Web Key）。");
        }

        return RoadRouteHealthResponse.builder()
                .amapDriving(amapDriving)
                .localGraph(localGraph)
                .amapSuccessCount(amapSuccessCount)
                .fallbackCount(fallbackCount)
                .localGraphSegments(localGraphSegments)
                .detail(detail.toString())
                .build();
    }
}
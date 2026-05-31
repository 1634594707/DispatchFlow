package com.fsd.dispatch.mapf;

import com.fsd.dispatch.config.MapfProperties;
import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.road.ParkRoadGraph;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 将路网节点按空间网格划分为 Zone（M5.1）。
 */
@Component
public class MapfZonePartitioner {

    private final MapfProperties mapfProperties;
    private final ParkPilotProperties parkPilotProperties;

    public MapfZonePartitioner(MapfProperties mapfProperties, ParkPilotProperties parkPilotProperties) {
        this.mapfProperties = mapfProperties;
        this.parkPilotProperties = parkPilotProperties;
    }

    public Map<String, String> partition(ParkRoadGraph graph) {
        Map<String, String> nodeZone = new HashMap<>();
        if (graph == null || graph.isEmpty()) {
            return nodeZone;
        }
        int grid = Math.max(2, mapfProperties.getZoneGridSize());
        int width = safe(parkPilotProperties.getWidth(), 1200);
        int height = safe(parkPilotProperties.getHeight(), 800);
        double cellW = width / (double) grid;
        double cellH = height / (double) grid;
        for (ParkRoadGraph.NodeView node : graph.nodes().values()) {
            nodeZone.put(node.code(), zoneCode(node.x(), node.y(), cellW, cellH, grid));
        }
        return nodeZone;
    }

    public String zoneOfNode(Map<String, String> nodeZone, String nodeCode) {
        return nodeZone.getOrDefault(nodeCode, "Z0");
    }

    private static String zoneCode(BigDecimal x, BigDecimal y, double cellW, double cellH, int grid) {
        if (x == null || y == null) {
            return "Z0";
        }
        int col = (int) Math.min(grid - 1, Math.max(0, Math.floor(x.doubleValue() / cellW)));
        int row = (int) Math.min(grid - 1, Math.max(0, Math.floor(y.doubleValue() / cellH)));
        return "Z" + row + "_" + col;
    }

    private static int safe(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}

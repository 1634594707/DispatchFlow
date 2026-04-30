package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkLayoutResponse {

    private boolean enabled;

    private Integer width;

    private Integer height;

    private Integer minZoom;

    private Integer maxZoom;

    private BigDecimal vehicleSpeedPxPerSecond;

    private String xFieldAlias;

    private String yFieldAlias;

    private List<ParkStationResponse> stations;

    private List<ParkPointResponse> parkingSpots;

    private List<ParkRoadNodeResponse> roadNodes;

    private List<ParkRoadSegmentResponse> roadSegments;
}

package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class RoadRouteValidateRequest {

    private BigDecimal originLng;

    private BigDecimal originLat;

    private BigDecimal destinationLng;

    private BigDecimal destinationLat;

    /** Optional explicit polyline (GCJ-02). When empty, plans route from origin/destination. */
    private List<RoadRoutePointDto> polyline;

    @Data
    public static class RoadRoutePointDto {
        private BigDecimal longitude;
        private BigDecimal latitude;
    }
}

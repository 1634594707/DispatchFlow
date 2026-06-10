package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadRouteValidateResponse {

    private boolean invalid;

    private boolean crossesBuilding;

    private boolean crossesRiver;

    private double nearestRoadDistanceMeters;

    private int vertexCount;

    private String source;
}

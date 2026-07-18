package com.fsd.admin.vo;

import java.util.List;
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

    /** P1-4: granular unreachable reason code (null = reachable). */
    private String unreachableReason;

    /** P1-4: human-readable detail for the unreachable reason. */
    private String unreachableDetail;

    /** P1-5: total route length in meters. */
    private double totalLengthMeters;

    /** P1-5: estimated travel time in seconds (based on per-segment speed limits). */
    private long estimatedTravelSeconds;

    /** P1-5: waiting time at service positions in seconds. */
    private long waitingSeconds;

    /** P1-5: charging time in seconds (if route includes a charging stop). */
    private long chargingSeconds;

    /** P1-5: risk points along the route (node pairs with gates / NO_STOP / etc). */
    private List<String> riskPoints;
}


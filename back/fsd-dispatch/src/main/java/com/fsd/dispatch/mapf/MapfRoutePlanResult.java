package com.fsd.dispatch.mapf;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapfRoutePlanResult {

    private List<com.fsd.dispatch.vo.ParkPointResponse> route;

    private boolean reserved;

    private int replanAttempts;

    private long planningTimeMs;

    public boolean isSuccess() {
        return route != null && !route.isEmpty();
    }
}

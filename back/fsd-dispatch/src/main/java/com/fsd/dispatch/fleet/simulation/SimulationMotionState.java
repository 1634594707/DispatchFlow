package com.fsd.dispatch.fleet.simulation;

import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class SimulationMotionState {

    public String stage;
    public Long taskId;
    public Long orderId;
    public BigDecimal targetX;
    public BigDecimal targetY;
    public String targetCode;
    public String targetType;
    public BigDecimal nextTargetX;
    public BigDecimal nextTargetY;
    public String nextTargetCode;
    public String nextTargetType;
    public BigDecimal lastX;
    public BigDecimal lastY;
    public ParkPointResponse standbyPoint;
    public ParkPointResponse chargingPoint;
    public LocalDateTime holdUntil;
    public LocalDateTime offlineUntil;
    public List<ParkPointResponse> route = List.of();
    public int routeIndex;
    public int busyMoveTicks;
    public boolean pluggedIn;
    public final Deque<ParkPointResponse> trail = new ArrayDeque<>();
}

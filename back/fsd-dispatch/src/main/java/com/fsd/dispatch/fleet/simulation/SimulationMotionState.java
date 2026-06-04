package com.fsd.dispatch.fleet.simulation;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteFollower;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
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
    public ParkPointResponse swapPoint;
    public int swapTicks;
    public LocalDateTime holdUntil;
    public LocalDateTime offlineUntil;
    public List<ParkPointResponse> route = List.of();
    public int routeIndex;
    public int busyMoveTicks;
    public boolean pluggedIn;
    public final Deque<ParkPointResponse> trail = new ArrayDeque<>();

    /** 当前路段道路 polyline 跟随器（M8）。 */
    public RoadRouteFollower geoFollower;

    /** 上次按里程扣电时的 geoFollower.traveledMeters。 */
    public double geoTraveledAtLastDrain;

    /** 计划路线 GCJ-02，供地图折线展示。 */
    public List<GeoPoint> plannedGeoPolyline = List.of();

    public BigDecimal geoLongitude;

    public BigDecimal geoLatitude;

    public double headingDegrees;

    public final List<GeoPoint> geoTrail = new ArrayList<>();

    public String routeSource;

    /** M8-R8：路线面域碰撞未通过时为 true。 */
    public boolean routeInvalid;
}

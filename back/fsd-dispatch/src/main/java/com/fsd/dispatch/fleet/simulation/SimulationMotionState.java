package com.fsd.dispatch.fleet.simulation;

import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteFollower;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 仿真运动状态：单台车的可变运行态。
 *
 * <p><b>线程契约（#25）</b>：state 由仿真 tick 线程写，但 HTTP 读线程会在
 * {@code buildSnapshots} 里对同一份 state 调 {@code publishTelemetry}——所以
 * <b>轨迹容器必须是并发容器</b>：{@code trail} 与 {@code geoTrail} 曾是
 * ArrayDeque/ArrayList，压测第 1 轮在读线程迭代时被 tick 线程的
 * add/淘汰打断，抛 {@code ConcurrentModificationException}（/park/vehicles 5xx 一次）。
 * {@code route} / {@code plannedGeoPolyline} 走整体引用替换，本就安全，保持 List。
 */
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
    /** 当前阶段开始时间，用于阶段超时判断（如 TO_PICKUP 超时转入 EMERGENCY_PARKING）。 */
    public LocalDateTime stageStartedAt;
    public List<ParkPointResponse> route = List.of();
    public int routeIndex;
    public int busyMoveTicks;
    public boolean pluggedIn;
    public final Deque<ParkPointResponse> trail = new ConcurrentLinkedDeque<>();

    /** 当前路段道路 polyline 跟随器（M8）。 */
    public RoadRouteFollower geoFollower;

    /** 上次按里程扣电时的 geoFollower.traveledMeters。 */
    public double geoTraveledAtLastDrain;

    /** 计划路线 GCJ-02，供地图折线展示。 */
    public List<GeoPoint> plannedGeoPolyline = List.of();

    public BigDecimal geoLongitude;

    public BigDecimal geoLatitude;

    public double headingDegrees;

    public final Deque<GeoPoint> geoTrail = new ConcurrentLinkedDeque<>();

    public String routeSource;

    /** M8-R8：路线面域碰撞未通过时为 true。 */
    public boolean routeInvalid;
}

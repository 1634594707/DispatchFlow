package com.fsd.dispatch.geo.local;

import com.fsd.dispatch.config.ParkPilotProperties;
import com.fsd.dispatch.geo.ParkGeoTransformService;
import com.fsd.dispatch.geo.ParkGeoTransformService.GeoPoint;
import com.fsd.dispatch.geo.RoadRouteResult;
import com.fsd.dispatch.geo.RoadRouteSource;
import com.fsd.dispatch.service.ParkRoutePlannerService;
import com.fsd.dispatch.vo.ParkPointResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * W2-e：把"画线/仿真用的路线"接到**现役 DB 路网**上。
 *
 * <p>为什么需要这一层（§13.89）：{@link LocalPilotRoadGraphService} 是链式服务里高德失败后的那一跳，
 * 但它读的是 {@code data/pilot_osm_geo.json} 那 30 条折线 —— 实测 bbox 只有 3.195 km²，
 * 而服务范围是它的好几倍。结果就是范围外绝大多数位置拿到的是**空折线的 STRAIGHT_LINE**，
 * 地图上看是"没有线"，画出来是"穿楼直线"。而 446 节点那张真图（边折线已由 §13.90 带进
 * {@link com.fsd.dispatch.road.ParkRoadGraph}）从来没有被这条路径用过。
 *
 * <p>刻意只做"翻译"，不复制判据：可达性与寻路全部交给 {@link ParkRoutePlannerService}，
 * 与派单/受理用的是**同一个 A*、同一张图** ⇒ 不会出现"画出来能走、派单说不可达"这种两套尺子对不上。
 *
 * <p>拿不到结果时一律返回 {@code null}（而不是给一条直线）：调用方要能区分
 * "这条路我算不出来" 与 "这条路是直线"。§7 明令不许静默兜底成穿墙直线。
 */
@Service
public class DbRoadGraphRouteService {

    private static final Logger log = LoggerFactory.getLogger(DbRoadGraphRouteService.class);

    private final ParkRoutePlannerService routePlannerService;
    private final ParkGeoTransformService transformService;
    private final ParkPilotProperties properties;

    public DbRoadGraphRouteService(ParkRoutePlannerService routePlannerService,
                                   ParkGeoTransformService transformService,
                                   ParkPilotProperties properties) {
        this.routePlannerService = routePlannerService;
        this.transformService = transformService;
        this.properties = properties;
    }

    /**
     * @return 沿真实道路几何的路线；图不可用 / 两端落不进示意坐标 / 图上不连通时返回 {@code null}
     */
    public RoadRouteResult plan(GeoPoint origin, GeoPoint destination) {
        if (origin == null || destination == null
                || origin.longitude() == null || origin.latitude() == null
                || destination.longitude() == null || destination.latitude() == null) {
            return miss("ENDPOINT_NO_GPS", origin, destination);
        }
        Long parkId = properties.getGeo().getRoutePlanParkId();
        if (parkId == null) {
            return miss("PARK_ID_UNSET", origin, destination);
        }
        Optional<ParkGeoTransformService.ParkPoint> start =
                transformService.fromGcj02(origin.longitude(), origin.latitude());
        Optional<ParkGeoTransformService.ParkPoint> end =
                transformService.fromGcj02(destination.longitude(), destination.latitude());
        if (start.isEmpty() || end.isEmpty()) {
            return miss("GCJ_TO_SCHEMATIC_EMPTY", origin, destination);
        }
        // 复用派单侧同一个可达性判据：不连通就别给线，让调用方去走它自己的回退分支
        if (!routePlannerService.isReachable(parkId, start.get().x(), start.get().y(),
                end.get().x(), end.get().y())) {
            return miss("NOT_REACHABLE", origin, destination);
        }

        List<GeoPoint> line = new ArrayList<>();
        line.add(origin);
        int intermediate = 0;
        for (ParkPointResponse point : routePlannerService.buildRoute(parkId,
                start.get().x(), start.get().y(), end.get().x(), end.get().y())) {
            if (point == null || point.getLongitude() == null || point.getLatitude() == null) {
                continue;   // START/END 不带 GPS，由上面/下面的 origin/destination 顶上
            }
            if ("START".equals(point.getCode()) || "END".equals(point.getCode())) {
                continue;
            }
            GeoPoint next = new GeoPoint(point.getLongitude(), point.getLatitude());
            if (isSameAsLast(line, next)) {
                continue;   // 相邻重复点会让折线长度与跟随器白算一帧
            }
            line.add(next);
            intermediate++;
        }
        if (!isSameAsLast(line, destination)) {
            line.add(destination);
        }
        if (intermediate == 0) {
            // 只剩两端 = 一条直线。这正是 §7 禁止的那种"静默兜底"，宁可交回调用方。
            return miss("NO_INTERMEDIATE_VERTEX", origin, destination);
        }
        return new RoadRouteResult(line, pathMeters(line), RoadRouteSource.LOCAL_GRAPH);
    }

    /**
     * 交回调用方前先点名是哪一道闸拦的。
     *
     * <p>这里原来是**五条 null 出口全静音**，于是"live 上拿不到实路折线"这件事在日志里不存在，
     * 只能靠读代码列四个候选猜（§13.98 就是那么卡的）。INFO 而不是 DEBUG：默认日志级别下要看得见。
     */
    private RoadRouteResult miss(String reason, GeoPoint origin, GeoPoint destination) {
        log.info("DB road graph declined route: reason={} {} -> {}", reason, origin, destination);
        return null;
    }

    private static boolean isSameAsLast(List<GeoPoint> line, GeoPoint next) {
        GeoPoint last = line.get(line.size() - 1);
        return last.longitude().compareTo(next.longitude()) == 0
                && last.latitude().compareTo(next.latitude()) == 0;
    }

    private static double pathMeters(List<GeoPoint> line) {
        double total = 0D;
        for (int i = 1; i < line.size(); i++) {
            total += com.fsd.dispatch.geo.GeoPolygonUtils.haversineMeters(line.get(i - 1), line.get(i));
        }
        return total;
    }

    /** 给测试与自检用：这条路径开没开。 */
    public boolean isEnabled() {
        return properties.getGeo().isRouteSourceFromGraph();
    }
}

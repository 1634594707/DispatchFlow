package com.fsd.dispatch.geo;

import com.fsd.dispatch.config.ParkPilotProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * W2-b：路线门禁。把"这条线能不能画出来"变成一个**有原因码的判定**，而不是继续让
 * {@link RoadRouteResult#isForbiddenFallback()} 当一个全仓零调用的死守卫（§13.88 量到的就是这件事）。
 *
 * <p><b>刻意不判的那一项</b>：{@code RoadRouteCollisionValidator.validate()} 一次算四件事
 * （{@code crossesBuilding / crossesRiver / offRoad / startOrEndInsideBuilding}），
 * 而 off-road 那项（&gt;45 m）参照的几何是 {@code pilot_osm_geo.json} 里那 <b>30 条折线、bbox 仅 3.195 km²</b>。
 * 服务范围是它的好几倍 ⇒ 把 {@code invalid} 直接当禁行，等于把绝大部分真路线判死、把派单整片关掉（§13.88）。
 * 所以这里<b>只判</b>：空折线 / 直线来源 / 明确压到建筑。
 * "建筑数据没覆盖到"会让 {@code crossesBuilding=false}，那是<b>弱通过</b>（没数据≠没穿楼），
 * 对外口径的上限见 §2 的口径红线。
 *
 * <p><b>两个开关分开</b>：
 * {@code fsd.park.geo.route-gate-enabled} 只做<b>度量与告警</b>（不改行为）；
 * {@code fsd.park.geo.route-gate-rejects} 才真的把线撤掉（返回空折线 ⇒ 下游 {@code routeInvalid} ⇒ 不画）。
 * 这么切是为了让"要不要拒、在哪一层拒"这个产品决定<b>可以凭计数器拍</b>：
 * 先只开 enabled，读 {@code dispatchflow_route_gate{outcome=...}} 看会撞上多少条，再决定要不要开 rejects。
 */
@Component
public class RoadRouteGate {

    /** 每次判定都记一笔，tag 区分过与不过的原因。 */
    public static final String METRIC = "dispatchflow.route.gate";

    private static final Logger log = LoggerFactory.getLogger(RoadRouteGate.class);

    private final MeterRegistry registry;
    private final ParkPilotProperties properties;

    public RoadRouteGate(MeterRegistry registry, ParkPilotProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    /**
     * @param rejected {@code true} 时调用方必须<b>别把这条线画出去</b>，并按 {@code reasonCode} 说明原因
     */
    public record Verdict(boolean rejected, String reasonCode) {
    }

    public Verdict evaluate(RoadRouteResult route, String context) {
        String reason = reasonCodeOf(route);
        registry.counter(METRIC, "outcome", reason == null ? "pass" : reason).increment();
        if (reason == null || !properties.getGeo().isRouteGateEnabled()) {
            return new Verdict(false, reason);
        }
        boolean rejects = properties.getGeo().isRouteGateRejects();
        log.warn("路线门禁命中：reason={} reject={} context={}", reason, rejects, context);
        return new Verdict(rejects, reason);
    }

    /** 不判 off-road / 不判 crossesRiver（原因见类注释）。{@code null} = 通过。 */
    public static String reasonCodeOf(RoadRouteResult route) {
        if (route == null) {
            return "ROUTE_RESULT_MISSING";
        }
        if (route.polyline().isEmpty()) {
            return "ROUTE_GEOMETRY_EMPTY";
        }
        if (route.source() == RoadRouteSource.STRAIGHT_LINE) {
            return "ROUTE_SOURCE_STRAIGHT_LINE";
        }
        if (route.crossesBuilding()) {
            return "ROUTE_CROSSES_BUILDING";
        }
        return null;
    }
}

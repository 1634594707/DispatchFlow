package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PostGIS 地理服务接入配置（GEO-PG）。
 *
 * <p>默认<b>关闭</b>：关闭时所有空间判断走原有 {@code GeoPolygonUtils} Java 手算路径，
 * 生产行为与接入前完全一致。开启后走 PostGIS，失败自动降级回手算，不抛出到调用方。
 *
 * <p>之所以能安全开启：{@code geo-py/tests/test_java_parity.py} 已在每张真实围栏的
 * 外接矩形上跑 60x60 网格，逐点比对 {@code ST_Contains} 与 {@code GeoPolygonUtils.contains}，
 * 要求 100% 一致。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsd.geo-service")
public class GeoServiceProperties {

    /** PostGIS 地理服务总开关，默认关闭。 */
    private boolean enabled = false;

    /** 服务基址。 */
    private String baseUrl = "http://127.0.0.1:8090";

    /** 单次请求超时（毫秒）。超时即降级，不拖慢派单热路径。 */
    private long timeoutMs = 200L;

    /** 最近站点召回的默认半径（米）。 */
    private double defaultRadiusM = 5000.0D;

    /** 连续失败多少次后临时熔断、直接走本地手算。0 表示不熔断。 */
    private int circuitBreakAfter = 3;

    /** 熔断冷却时间（毫秒），冷却后半开重试一次。 */
    private long circuitCooldownMs = 30_000L;
}

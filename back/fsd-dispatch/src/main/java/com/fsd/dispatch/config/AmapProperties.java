package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 高德 Web 服务 Key（与前端 JS API Key 分离）及物流矩阵配置。M4
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsd.amap")
public class AmapProperties {

    /** Web 服务 Key，用于后端 distance/matrix API；勿与 JS API Key 混用。 */
    private String webServiceKey = "";

    private LogisticsConfig logistics = new LogisticsConfig();

    @Data
    public static class LogisticsConfig {

        /** 启用后 REAL 派单评分可叠加公开道路距离估算。 */
        private boolean enabled = false;

        /** 0=仅园区路网；1=完全采用高德矩阵距离（米→px 换算后参与评分）。 */
        private double blendWeight = 0.3;

        /** 单次矩阵请求超时（毫秒）。 */
        private int timeoutMs = 3000;

        /** API 基础 URL。 */
        private String baseUrl = "https://restapi.amap.com/v3/distance";
    }
}

package com.fsd.admin.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 单个站点的 24 小时补能需求剖面（管理端只读视图）。
 *
 * <p>{@code stale} 为 true 表示该站最新一行已超出 {@code max-data-age-hours}：
 * 此时派单侧<b>不会</b>使用该预测（已回退纯阈值补能策略），前端应如实标注而非隐藏。
 */
@Data
@Builder
public class AdminAnalyticsEnergyForecastStationItem {

    private Long parkId;

    private Long stationId;

    private String stationCode;

    private String modelVersion;

    /** 该站最新一行的生成时间。 */
    private LocalDateTime generatedAt;

    /** 最新一行是否已超期（超期即派单侧不再采纳）。 */
    private boolean stale;

    private int sampleCount;

    /** 剖面实际小时数；正常情况下为 24，缺小时不会补齐。 */
    private int hourCount;

    /** 全日 pressureP95 峰值。 */
    private BigDecimal peakPressureP95;

    /** 峰值压力所在小时（0-23）。 */
    private int peakHourOfDay;

    /** 全日 demandP90 峰值。 */
    private BigDecimal peakDemandP90;

    /**
     * 峰值压力是否达到配置阈值。
     *
     * <p>事实陈述，仅供观察；当前配置阈值与实际压力量纲不一致（见实施记录 B4），
     * 故前端应以中性文案呈现，不作为通过/失败判定。
     */
    private boolean pressureThresholdExceeded;

    private List<AdminAnalyticsEnergyForecastHourPoint> hours;
}

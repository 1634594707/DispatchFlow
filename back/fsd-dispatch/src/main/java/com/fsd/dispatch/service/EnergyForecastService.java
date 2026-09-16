package com.fsd.dispatch.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 站点补能需求预测读取入口（ALG-FC）。
 *
 * <p>数据由离线脚本（scripts/ml/energy_demand_forecast.py）训练并导入 t_energy_forecast。
 * 本服务只读，且当预测缺失/超期时所有判断退化为"无压力"，不影响安全相关的补能决策。
 */
public interface EnergyForecastService {

    /** 预测读取是否开启（配置开关）。 */
    boolean isEnabled();

    /** 指定日期的当前小时、指定站点的预测；无数据或已超期返回空。 */
    Optional<StationDemandForecast> currentHourForecast(LocalDate date, Long parkId, Long stationId);

    /** 园区当前小时的最大到站压力（各站 pressureP95 取最大）；无数据返回 0。 */
    double parkPressure(LocalDate date, Long parkId);

    /**
     * 是否应因补能高峰而推迟返充。
     *
     * <p>安全优先：SOC 距离临界阈值不足 {@code deferMarginSoc} 时永不推迟。
     * 无预测数据时返回 false（等价于既有纯阈值策略）。
     */
    boolean shouldDeferReturnToCharge(LocalDate date, Long parkId, Integer batteryLevel);

    /**
     * 园区内各站点的 24 小时需求剖面（供管理端可视化）。
     *
     * <p>与 {@link #currentHourForecast} 的差异：**不做"当前小时"过滤**，返回当天全部小时。
     * 同一小时存在多个 {@code model_version} 时取 {@code generatedAt} 最新的那条，
     * 与派单侧的解析口径一致。
     *
     * <p>数据是否超期由 {@link StationHourlyProfile#stale()} 标出而**不隐藏**：
     * 前端需要如实区分"有预测且在有效期"与"有行但已失效（服务已回退纯阈值策略）"。
     */
    List<StationHourlyProfile> parkHourlyProfiles(LocalDate date, Long parkId);

    /** 单站单小时的需求点。 */
    record HourlyDemandPoint(int hourOfDay,
                             BigDecimal demandP50,
                             BigDecimal demandP90,
                             BigDecimal pressureP95) {
    }

    /**
     * 单站 24 小时剖面。
     *
     * @param stale 该站最新一行的 {@code generatedAt} 是否已超出 {@code max-data-age-hours}
     */
    record StationHourlyProfile(Long parkId,
                                Long stationId,
                                String stationCode,
                                LocalDate forecastDate,
                                int sampleCount,
                                String modelVersion,
                                LocalDateTime generatedAt,
                                boolean stale,
                                List<HourlyDemandPoint> hours) {
    }

    /**
     * 单站补能需求预测。
     *
     * @param demandP50    需求中位数（次/小时）
     * @param demandP90    需求 P90 上界（次/小时）
     * @param pressureP95  滑动窗口 P95 到站压力峰值
     * @param sampleCount  训练样本数
     * @param modelVersion 模型版本标识
     */
    record StationDemandForecast(Long parkId,
                                 Long stationId,
                                 String stationCode,
                                 LocalDate forecastDate,
                                 int hourOfDay,
                                 BigDecimal demandP50,
                                 BigDecimal demandP90,
                                 BigDecimal pressureP95,
                                 int sampleCount,
                                 String modelVersion) {
    }
}

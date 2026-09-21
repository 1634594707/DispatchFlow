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
     * 解析"为什么这个 0 是 0"——把退化原因从 {@link #parkPressure} 的 0 里拆出来。
     *
     * <p>{@code parkPressure} 在开关关闭、没有行、没有当前小时、已超期四种情形下都返回 0，
     * 运维上无法区分"补能确实空闲"与"日作业没跑"。本方法是这个区分在本仓的**唯一权威口径**，
     * 也是路线图 M4 第 3 条要求的可观测事件的数据来源。
     */
    ForecastStatus parkForecastStatus(LocalDate date, Long parkId);

    /**
     * 是否应因补能高峰而推迟返充。
     *
     * <p>安全优先：SOC 距离临界阈值不足 {@code deferMarginSoc} 时永不推迟。
     * 无预测数据时返回 false（等价于既有纯阈值策略）。
     *
     * <p>返回 false 的原因分两类且**必须可观测**：SOC 已在临界余量内（安全侧，不该报警），
     * 以及预测不可用（{@link ForecastAvailability} 非 {@code FRESH}，日作业没跑就属于这一类，
     * 由 {@code EnergyForecastMetrics} 记计数器并限速打 WARN）。
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

    /** 预测可用的五种口径之一；顺序即诊断顺序，见 {@link #parkForecastStatus}。 */
    enum ForecastAvailability {
        /** {@code fsd.energy-forecast.enabled=false}：人为关掉读取。 */
        DISABLED,
        /** 当天该园区一行都没有 —— 日作业没跑、跑失败，或日期/园区对不上。 */
        NO_ROWS,
        /** 当天有行但没有当前小时的行 —— 剖面缺该小时，或生成端与服务端时钟不一致。 */
        NOT_THIS_HOUR,
        /** 当天有行，但没有任何行落在 {@code max-data-age-hours} 窗口内 —— 日作业至少停了这么久。 */
        STALE,
        /** 新鲜且覆盖当前小时，压力值可用。 */
        FRESH
    }

    /**
     * 一次预测可用性解析的结果。
     *
     * @param rowsForDate       当天该园区的行数（未经有效期与小时过滤）
     * @param rowsForHour       其中属于当前小时且在有效期内的行数，即真正参与 {@code pressure} 的行数
     * @param newestGeneratedAt 当天最新一行的生成时间；{@code null} = 日作业从未成功落库或该行未写时间戳
     * @param pressure          与 {@link #parkPressure} 同口径的压力值（退化时为 0）
     * @param dayMedianPressure 当天"逐小时园区压力"的中位数，用来判断剖面有没有高峰可言
     */
    record ForecastStatus(ForecastAvailability availability,
                          Long parkId,
                          LocalDate forecastDate,
                          int hourOfDay,
                          int rowsForDate,
                          int rowsForHour,
                          LocalDateTime newestGeneratedAt,
                          int maxDataAgeHours,
                          double pressure,
                          double dayMedianPressure) {

        public boolean usable() {
            return availability == ForecastAvailability.FRESH;
        }

        /**
         * 当前小时相对当日中位的 prominence。
         *
         * <p>中位为 0 而当前小时非 0 时返回 {@code +∞}：那不是"没有高峰"，那是最尖锐的高峰
         * （一天里大多数小时根本到不了站）。中位与当前小时都为 0 时才退化成 1。
         */
        public double peakRatio() {
            if (dayMedianPressure <= 0D) {
                return pressure > 0D ? Double.POSITIVE_INFINITY : 1D;
            }
            return pressure / dayMedianPressure;
        }

        /** 一行中文说明，直接进日志/告警，不再要求读代码才能判断退化原因。 */
        public String explain() {
            return switch (availability) {
                case DISABLED -> "预测读取开关已关闭（fsd.energy-forecast.enabled=false）";
                case NO_ROWS -> "t_energy_forecast 在 " + forecastDate + " 无 park=" + parkId
                        + " 的行——日作业未运行、运行失败，或日期/园区对不上";
                case NOT_THIS_HOUR -> forecastDate + " 有 " + rowsForDate + " 行且都在 "
                        + maxDataAgeHours + "h 有效期内，但没有当前小时 " + hourOfDay + " 的行（最新生成时间："
                        + generatedText() + "）——剖面缺该小时或生成端与服务端时钟不一致";
                case STALE -> forecastDate + " 有 " + rowsForDate + " 行，但没有一行落在 "
                        + maxDataAgeHours + "h 有效期内（最新 generated_at=" + generatedText()
                        + "）——日作业至少已停摆这么久";
                case FRESH -> "可用：小时 " + hourOfDay + " 共 " + rowsForHour + " 行，pressureP95 最大 "
                        + fmt(pressure) + "，当日逐小时中位 " + fmt(dayMedianPressure)
                        + "，prominence " + String.format("%.2f", peakRatio()) + "x";
            };
        }

        private static String fmt(double value) {
            return String.format("%.2f", value);
        }

        private String generatedText() {
            return newestGeneratedAt == null ? "未知，行未写 generated_at" : newestGeneratedAt.toString();
        }
    }

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

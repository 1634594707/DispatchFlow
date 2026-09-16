package com.fsd.dispatch.service;

import java.math.BigDecimal;
import java.time.LocalDate;
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

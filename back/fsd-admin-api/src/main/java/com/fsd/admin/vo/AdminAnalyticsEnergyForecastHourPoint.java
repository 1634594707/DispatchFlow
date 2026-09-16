package com.fsd.admin.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

/**
 * 补能需求预测剖面上的单点（某站某小时）。
 *
 * <p>对应 t_energy_forecast 一行；缺失小时不会被补齐，字段不会为 null（缺值以 0 表达）。
 */
@Data
@Builder
public class AdminAnalyticsEnergyForecastHourPoint {

    /** 小时（0-23）。 */
    private int hourOfDay;

    /** 需求中位数（次/小时）。 */
    private BigDecimal demandP50;

    /** 需求 P90 上界（次/小时）。 */
    private BigDecimal demandP90;

    /** 滑动窗口 P95 到站压力峰值。 */
    private BigDecimal pressureP95;
}

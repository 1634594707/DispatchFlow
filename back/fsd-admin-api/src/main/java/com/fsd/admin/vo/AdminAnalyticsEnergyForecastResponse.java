package com.fsd.admin.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 管理端补能需求预测视图（ALG-FC 只读）。
 *
 * <p>数据来源：离线训练脚本写入的 t_energy_forecast。本接口<b>只读</b>，
 * 不触发训练、不写库、不影响派单语义。
 *
 * <p>本次刻意暴露 {@code pressureThreshold} 与每站 {@code stale}：
 * 派单侧在预测超期时会静默回退纯阈值策略，若前端只看曲线会误以为预测仍在生效。
 */
@Data
@Builder
public class AdminAnalyticsEnergyForecastResponse {

    /** 预测读取总开关；为 false 时 stations 必为空。 */
    private boolean enabled;

    /** 当前生效的压力阈值（配置值 fsd.energy-forecast.pressure-threshold）。 */
    private double pressureThreshold;

    /** 预测数据最大有效期（小时）（fsd.energy-forecast.max-data-age-hours）。 */
    private int maxDataAgeHours;

    /** 实际查询的预测日期。 */
    private LocalDate forecastDate;

    /** 解析后的园区 ID（请求未指定时取默认园区）。 */
    private Long parkId;

    /** 接口响应时刻，供前端定位"当前小时"。 */
    private LocalDateTime serverTime;

    private int stationCount;

    /** 是否至少有一个站点存在预测行。 */
    private boolean anyData;

    /** 是否存在"有行但已超期"的站点——true 表示派单侧已不使用这些预测。 */
    private boolean anyStale;

    private List<AdminAnalyticsEnergyForecastStationItem> stations;
}

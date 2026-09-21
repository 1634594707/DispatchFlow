package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fsd.dispatch.config.EnergyForecastProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.EnergyForecastEntity;
import com.fsd.dispatch.mapper.EnergyForecastMapper;
import com.fsd.dispatch.metrics.EnergyForecastMetrics;
import com.fsd.dispatch.service.EnergyForecastService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 补能需求预测读取实现（ALG-FC）。
 *
 * <p>三条硬约束，保证"没有模型数据时系统行为不变"：
 * <ol>
 *   <li>只读 t_energy_forecast，绝不写入订单/充电/车辆等核心业务表</li>
 *   <li>预测缺失或超出有效期（{@code max-data-age-hours}）→ 视为无压力，回退纯阈值策略</li>
 *   <li>安全优先：SOC 余量不足时永不推迟返充，无论预测怎么说</li>
 * </ol>
 *
 * <p>第 2 条的"回退"必须是**可观测**的：{@link #parkForecastStatus} 给出退化的确切原因，
 * 并在错峰判断命中时经 {@link EnergyForecastMetrics} 落到指标与限速日志。
 */
@Slf4j
@Service
public class EnergyForecastServiceImpl implements EnergyForecastService {

    private final EnergyForecastProperties properties;
    private final FleetEnergyProperties fleetEnergyProperties;
    private final EnergyForecastMapper energyForecastMapper;
    private final EnergyForecastMetrics forecastMetrics;

    public EnergyForecastServiceImpl(EnergyForecastProperties properties,
                                     FleetEnergyProperties fleetEnergyProperties,
                                     EnergyForecastMapper energyForecastMapper,
                                     EnergyForecastMetrics forecastMetrics) {
        this.properties = properties;
        this.fleetEnergyProperties = fleetEnergyProperties;
        this.energyForecastMapper = energyForecastMapper;
        this.forecastMetrics = forecastMetrics;
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public Optional<StationDemandForecast> currentHourForecast(LocalDate date, Long parkId, Long stationId) {
        if (!isEnabled() || date == null || parkId == null || stationId == null) {
            return Optional.empty();
        }
        List<EnergyForecastEntity> rows = queryRows(date, parkId);
        return rows.stream()
                .filter(row -> stationId.equals(row.getStationId()))
                .max(Comparator.comparing(EnergyForecastEntity::getGeneratedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(this::toForecast);
    }

    @Override
    public double parkPressure(LocalDate date, Long parkId) {
        return parkForecastStatus(date, parkId).pressure();
    }

    @Override
    public ForecastStatus parkForecastStatus(LocalDate date, Long parkId) {
        int hourOfDay = LocalDateTime.now().getHour();
        int maxAgeHours = Math.max(0, properties.getMaxDataAgeHours());
        if (!isEnabled()) {
            return new ForecastStatus(ForecastAvailability.DISABLED, parkId, date,
                    hourOfDay, 0, 0, null, maxAgeHours, 0D, 0D);
        }
        if (date == null || parkId == null) {
            // 参数缺失按"没有行"归类：同样落到纯阈值策略，且在日志里与真实空表无法区分是诚实的
            return new ForecastStatus(ForecastAvailability.NO_ROWS, parkId, date,
                    hourOfDay, 0, 0, null, maxAgeHours, 0D, 0D);
        }

        List<EnergyForecastEntity> rowsForDate = energyForecastMapper.selectList(
                new QueryWrapper<EnergyForecastEntity>()
                        .eq("park_id", parkId)
                        .eq("forecast_date", date)
                        .eq("deleted", 0));
        if (rowsForDate.isEmpty()) {
            return new ForecastStatus(ForecastAvailability.NO_ROWS, parkId, date, hourOfDay,
                    0, 0, null, maxAgeHours, 0D, 0D);
        }
        LocalDateTime newestGeneratedAt = rowsForDate.stream()
                .map(EnergyForecastEntity::getGeneratedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        LocalDateTime oldestAccepted = LocalDateTime.now().minusHours(maxAgeHours);
        // 先判超期再判小时：日作业停摆时"所有行都过期"是更靠近根因的那一档，
        // 若反过来先判小时，会把"昨天跑过一次"误报成"剖面缺这个小时"。
        List<EnergyForecastEntity> inWindow = rowsForDate.stream()
                .filter(row -> isWithinAge(row, oldestAccepted))
                .toList();
        if (inWindow.isEmpty()) {
            return new ForecastStatus(ForecastAvailability.STALE, parkId, date, hourOfDay,
                    rowsForDate.size(), 0, newestGeneratedAt, maxAgeHours, 0D, 0D);
        }
        List<EnergyForecastEntity> usable = inWindow.stream()
                .filter(row -> isCurrentHour(row, hourOfDay))
                .toList();
        if (usable.isEmpty()) {
            return new ForecastStatus(ForecastAvailability.NOT_THIS_HOUR, parkId, date, hourOfDay,
                    rowsForDate.size(), 0, newestGeneratedAt, maxAgeHours, 0D,
                    median(hourlyParkMaxima(inWindow)));
        }
        double pressure = maxPressure(usable);
        return new ForecastStatus(ForecastAvailability.FRESH, parkId, date, hourOfDay,
                rowsForDate.size(), usable.size(), newestGeneratedAt, maxAgeHours, pressure,
                median(hourlyParkMaxima(inWindow)));
    }

    @Override
    public boolean shouldDeferReturnToCharge(LocalDate date, Long parkId, Integer batteryLevel) {
        if (!isEnabled() || !properties.isDeferReturnEnabled() || batteryLevel == null) {
            return false;
        }
        int deferFloor = fleetEnergyProperties.getCriticalSocThreshold() + properties.getDeferMarginSoc();
        if (batteryLevel <= deferFloor) {
            // 安全优先：接近临界 SOC 时必须立即返充，不参与错峰。这条与预测可用性无关，故不记退化
            return false;
        }
        ForecastStatus status = parkForecastStatus(date, parkId);
        forecastMetrics.observe(parkId, status);
        if (!status.usable()) {
            return false;
        }
        if (status.pressure() < properties.getPressureThreshold()) {
            return false;
        }
        // 绝对阈值只回答"够不够忙"，回答不了"这算不算高峰"。压测夹具的剖面是 150 次/小时的平地
        // （实测峰谷比 1.08），绝对阈值对它恒成立；没有这道相对判据，日作业一旦调度起来，
        // 错峰返充就会从"无操作"直接翻成"整天推迟"。
        if (!peakIsResolvable(status)) {
            forecastMetrics.observeFlatProfile(parkId, status);
            return false;
        }
        if (log.isDebugEnabled()) {
            log.debug("deferring return-to-charge: parkId={}, soc={}, pressureP95={}, threshold={}, prominence={}x",
                    parkId, batteryLevel, status.pressure(), properties.getPressureThreshold(), status.peakRatio());
        }
        return true;
    }

    private boolean peakIsResolvable(ForecastStatus status) {
        double minRatio = properties.getMinPeakPressureRatio();
        return minRatio <= 0D || status.peakRatio() >= minRatio;
    }

    /** 逐小时取园区内各站压力最大值 —— 与 {@link #parkPressure} 同口径，只是铺开成一天的序列。 */
    private static List<Double> hourlyParkMaxima(List<EnergyForecastEntity> rows) {
        Map<Integer, Double> byHour = new TreeMap<>();
        for (EnergyForecastEntity row : rows) {
            if (row.getHourOfDay() == null) {
                continue;
            }
            byHour.merge(row.getHourOfDay(), maxPressure(List.of(row)), Math::max);
        }
        return List.copyOf(byHour.values());
    }

    private static double maxPressure(List<EnergyForecastEntity> rows) {
        return rows.stream()
                .map(EnergyForecastEntity::getPressureP95)
                .filter(Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0D);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0D;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2D;
    }

    @Override
    public List<StationHourlyProfile> parkHourlyProfiles(LocalDate date, Long parkId) {
        if (!isEnabled() || date == null || parkId == null) {
            return List.of();
        }
        List<EnergyForecastEntity> rows = energyForecastMapper.selectList(
                new QueryWrapper<EnergyForecastEntity>()
                        .eq("park_id", parkId)
                        .eq("forecast_date", date)
                        .eq("deleted", 0));
        if (rows.isEmpty()) {
            return List.of();
        }

        // 站点 -> 小时 -> 该小时最新的一行（同一小时可能因换 model_version 而存在多行）
        Map<Long, Map<Integer, EnergyForecastEntity>> byStation = new TreeMap<>();
        for (EnergyForecastEntity row : rows) {
            if (row.getStationId() == null || row.getHourOfDay() == null) {
                continue;
            }
            byStation.computeIfAbsent(row.getStationId(), key -> new TreeMap<>())
                    .merge(row.getHourOfDay(), row, EnergyForecastServiceImpl::newer);
        }

        LocalDateTime oldestAccepted = LocalDateTime.now()
                .minusHours(Math.max(0, properties.getMaxDataAgeHours()));
        List<StationHourlyProfile> profiles = new ArrayList<>(byStation.size());
        byStation.forEach((stationId, hours) -> {
            EnergyForecastEntity newest = hours.values().stream()
                    .max(Comparator.comparing(EnergyForecastEntity::getGeneratedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElseThrow();
            LocalDateTime generatedAt = newest.getGeneratedAt();
            boolean stale = generatedAt == null || generatedAt.isBefore(oldestAccepted);
            List<HourlyDemandPoint> points = hours.values().stream()
                    .map(row -> new HourlyDemandPoint(row.getHourOfDay(),
                            nullSafe(row.getDemandP50()),
                            nullSafe(row.getDemandP90()),
                            nullSafe(row.getPressureP95())))
                    .toList();
            profiles.add(new StationHourlyProfile(
                    newest.getParkId() == null ? parkId : newest.getParkId(),
                    stationId,
                    newest.getStationCode(),
                    newest.getForecastDate() == null ? date : newest.getForecastDate(),
                    newest.getSampleCount() == null ? 0 : newest.getSampleCount(),
                    newest.getModelVersion(),
                    generatedAt,
                    stale,
                    points));
        });
        return profiles;
    }

    private static EnergyForecastEntity newer(EnergyForecastEntity left, EnergyForecastEntity right) {
        if (left.getGeneratedAt() == null) {
            return right;
        }
        if (right.getGeneratedAt() == null) {
            return left;
        }
        return left.getGeneratedAt().isAfter(right.getGeneratedAt()) ? left : right;
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<EnergyForecastEntity> queryRows(LocalDate date, Long parkId) {
        LocalDateTime oldestAccepted = LocalDateTime.now()
                .minusHours(Math.max(0, properties.getMaxDataAgeHours()));
        int hourOfDay = LocalDateTime.now().getHour();
        List<EnergyForecastEntity> rows = energyForecastMapper.selectList(new QueryWrapper<EnergyForecastEntity>()
                .eq("park_id", parkId)
                .eq("forecast_date", date)
                .eq("deleted", 0));
        return rows.stream()
                .filter(row -> isWithinAge(row, oldestAccepted))
                .filter(row -> isCurrentHour(row, hourOfDay))
                .toList();
    }

    /** 与 {@link #parkForecastStatus} 共用同一判据，避免"读取路径"和"诊断路径"对超期的定义漂移。 */
    private static boolean isWithinAge(EnergyForecastEntity row, LocalDateTime oldestAccepted) {
        return row.getGeneratedAt() == null || !row.getGeneratedAt().isBefore(oldestAccepted);
    }

    private static boolean isCurrentHour(EnergyForecastEntity row, int hourOfDay) {
        return row.getHourOfDay() != null && row.getHourOfDay() == hourOfDay;
    }

    private StationDemandForecast toForecast(EnergyForecastEntity row) {
        return new StationDemandForecast(
                row.getParkId(),
                row.getStationId(),
                row.getStationCode(),
                row.getForecastDate(),
                row.getHourOfDay() == null ? 0 : row.getHourOfDay(),
                row.getDemandP50() == null ? BigDecimal.ZERO : row.getDemandP50(),
                row.getDemandP90() == null ? BigDecimal.ZERO : row.getDemandP90(),
                row.getPressureP95() == null ? BigDecimal.ZERO : row.getPressureP95(),
                row.getSampleCount() == null ? 0 : row.getSampleCount(),
                row.getModelVersion());
    }
}

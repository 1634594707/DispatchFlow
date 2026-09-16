package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fsd.dispatch.config.EnergyForecastProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;
import com.fsd.dispatch.entity.EnergyForecastEntity;
import com.fsd.dispatch.mapper.EnergyForecastMapper;
import com.fsd.dispatch.service.EnergyForecastService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
 */
@Slf4j
@Service
public class EnergyForecastServiceImpl implements EnergyForecastService {

    private final EnergyForecastProperties properties;
    private final FleetEnergyProperties fleetEnergyProperties;
    private final EnergyForecastMapper energyForecastMapper;

    public EnergyForecastServiceImpl(EnergyForecastProperties properties,
                                     FleetEnergyProperties fleetEnergyProperties,
                                     EnergyForecastMapper energyForecastMapper) {
        this.properties = properties;
        this.fleetEnergyProperties = fleetEnergyProperties;
        this.energyForecastMapper = energyForecastMapper;
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
        if (!isEnabled() || date == null || parkId == null) {
            return 0D;
        }
        return queryRows(date, parkId).stream()
                .map(EnergyForecastEntity::getPressureP95)
                .filter(java.util.Objects::nonNull)
                .mapToDouble(BigDecimal::doubleValue)
                .max()
                .orElse(0D);
    }

    @Override
    public boolean shouldDeferReturnToCharge(LocalDate date, Long parkId, Integer batteryLevel) {
        if (!isEnabled() || !properties.isDeferReturnEnabled() || batteryLevel == null) {
            return false;
        }
        int deferFloor = fleetEnergyProperties.getCriticalSocThreshold() + properties.getDeferMarginSoc();
        if (batteryLevel <= deferFloor) {
            // 安全优先：接近临界 SOC 时必须立即返充，不参与错峰
            return false;
        }
        double pressure = parkPressure(date, parkId);
        if (pressure < properties.getPressureThreshold()) {
            return false;
        }
        if (log.isDebugEnabled()) {
            log.debug("deferring return-to-charge: parkId={}, soc={}, pressureP95={}, threshold={}",
                    parkId, batteryLevel, pressure, properties.getPressureThreshold());
        }
        return true;
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
        LocalDateTime oldestAccepted = LocalDateTime.now().minusHours(Math.max(0, properties.getMaxDataAgeHours()));
        List<EnergyForecastEntity> rows = energyForecastMapper.selectList(new QueryWrapper<EnergyForecastEntity>()
                .eq("park_id", parkId)
                .eq("forecast_date", date)
                .eq("deleted", 0));
        return rows.stream()
                .filter(row -> row.getGeneratedAt() == null || !row.getGeneratedAt().isBefore(oldestAccepted))
                .filter(row -> row.getHourOfDay() != null
                        && row.getHourOfDay() == LocalDateTime.now().getHour())
                .toList();
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

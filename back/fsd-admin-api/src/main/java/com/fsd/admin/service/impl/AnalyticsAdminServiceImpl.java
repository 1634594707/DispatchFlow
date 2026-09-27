package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.common.exception.BusinessException;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.admin.vo.AdminAnalyticsChargingHistoryItem;
import com.fsd.admin.vo.AdminAnalyticsChainKpiResponse;
import com.fsd.admin.vo.AdminAnalyticsChargingOverviewResponse;
import com.fsd.admin.vo.AdminAnalyticsChargingSessionItem;
import com.fsd.admin.vo.AdminAnalyticsDailySummaryResponse;
import com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastHourPoint;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastResponse;
import com.fsd.admin.vo.AdminAnalyticsEnergyForecastStationItem;
import com.fsd.admin.vo.AdminAnalyticsExceptionResponse;
import com.fsd.admin.vo.AdminAnalyticsHourlyPoint;
import com.fsd.admin.vo.AdminAnalyticsTrendPoint;
import com.fsd.admin.vo.AdminAnalyticsParkCompareItem;
import com.fsd.admin.vo.AdminAnalyticsTypeCount;
import com.fsd.dispatch.config.EnergyForecastProperties;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.service.EnergyForecastService;
import com.fsd.common.enums.ChargingSessionStatus;
import com.fsd.admin.vo.AdminPeakCompareResponse;
import com.fsd.dispatch.entity.BatterySwapSessionEntity;
import com.fsd.dispatch.mapper.BatterySwapSessionMapper;
import com.fsd.dispatch.entity.ChargingPileEntity;
import com.fsd.dispatch.entity.ChargingSessionEntity;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ChargingSessionMapper;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsAdminServiceImpl implements AnalyticsAdminService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    /** 单次导出最大数据行数（路线图 Phase 5）；超限提示改用报表计划/历史报表。 */
    @Value("${fsd.admin.export.max-rows:50000}")
    private int exportMaxRows;

    private final OrderMapper orderMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final ChargingSessionMapper chargingSessionMapper;
    private final BatterySwapSessionMapper batterySwapSessionMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final VehicleMapper vehicleMapper;
    private final FleetRuntimeService fleetRuntimeService;
    private final ParkMapper parkMapper;
    private final AdminParkScopeService adminParkScopeService;
    private final MeterRegistry meterRegistry;
    private final EnergyForecastService energyForecastService;
    private final EnergyForecastProperties energyForecastProperties;
    /** P1-1：调度指标的低 SOC 线等阈值统一从补能策略出口取（Redis 热更新 + YAML 回退）。 */
    private final com.fsd.dispatch.fleet.policy.FleetChargePolicy fleetChargePolicy;

    public AnalyticsAdminServiceImpl(OrderMapper orderMapper,
                                     DispatchTaskMapper dispatchTaskMapper,
                                     DispatchExceptionRecordMapper exceptionRecordMapper,
                                     ChargingSessionMapper chargingSessionMapper,
                                     BatterySwapSessionMapper batterySwapSessionMapper,
                                     ChargingPileMapper chargingPileMapper,
                                     VehicleMapper vehicleMapper,
                                     FleetRuntimeService fleetRuntimeService,
                                     ParkMapper parkMapper,
                                     AdminParkScopeService adminParkScopeService,
                                     MeterRegistry meterRegistry,
                                     EnergyForecastService energyForecastService,
                                     EnergyForecastProperties energyForecastProperties,
                                     com.fsd.dispatch.fleet.policy.FleetChargePolicy fleetChargePolicy) {
        this.orderMapper = orderMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.chargingSessionMapper = chargingSessionMapper;
        this.batterySwapSessionMapper = batterySwapSessionMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.vehicleMapper = vehicleMapper;
        this.fleetRuntimeService = fleetRuntimeService;
        this.parkMapper = parkMapper;
        this.adminParkScopeService = adminParkScopeService;
        this.meterRegistry = meterRegistry;
        this.energyForecastService = energyForecastService;
        this.energyForecastProperties = energyForecastProperties;
        this.fleetChargePolicy = fleetChargePolicy;
    }

    @Override
    public AdminAnalyticsEfficiencyResponse getEfficiency(String period, Long parkId) {
        String normalized = normalizePeriod(period);
        LocalDateTime start = rangeStart(normalized);
        List<OrderEntity> orders = filterOrdersByPark(loadOrdersSince(start), parkId);
        List<DispatchTaskEntity> tasks = filterTasksByPark(loadTasksSince(start), parkId);

        List<AdminAnalyticsTrendPoint> trend = buildOrderTrend(orders, normalized, start);
        double avgDuration = tasks.stream()
                .filter(task -> "SUCCESS".equals(task.getStatus()))
                .filter(task -> task.getStartTime() != null && task.getFinishTime() != null)
                .mapToLong(task -> Duration.between(task.getStartTime(), task.getFinishTime()).toMinutes())
                .average()
                .orElse(0D);

        long busyTasks = tasks.stream().filter(task -> "EXECUTING".equals(task.getStatus())).count();
        List<VehicleEntity> onlineFleet = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0)
                        .eq(VehicleEntity::getOnlineStatus, "ONLINE"))
                .stream()
                .filter(vehicle -> matchesVehicleEntityPark(vehicle, parkId))
                .toList();
        long onlineVehicles = onlineFleet.size();
        double utilization = onlineVehicles <= 0 ? 0D : (double) busyTasks / onlineVehicles;

        List<AdminAnalyticsHourlyPoint> peakHours = buildPeakHours(orders, tasks);
        com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse.DispatchMetrics dispatchMetrics =
                buildDispatchMetrics(onlineFleet, orders);

        return AdminAnalyticsEfficiencyResponse.builder()
                .period(normalized)
                .orderCompletionTrend(trend)
                .avgTaskDurationMinutes(round1(avgDuration))
                .vehicleUtilizationRate(round1(utilization * 100))
                .peakHours(peakHours)
                .dispatchMetrics(dispatchMetrics)
                .build();
    }

    @Override
    public com.fsd.admin.vo.AdminAnalyticsStationHourResponse getStationHourlyDemand(String period, Long parkId) {
        String normalized = normalizePeriod(period);
        List<OrderEntity> orders = filterOrdersByPark(loadOrdersSince(rangeStart(normalized)), parkId);
        return com.fsd.admin.vo.AdminAnalyticsStationHourResponse.builder()
                .period(normalized)
                .rows(buildStationHourRows(orders))
                .build();
    }

    /**
     * P1-1：站点×小时聚合（页面 /station-hourly 与导出 dataset=station-hourly 共用本方法，天然一致）。
     * 站点 = 订单取货节点编码（pickupNodeCode，空记"未知"）；小时 = createdAt 本地小时；值为窗口内订单数。
     */
    java.util.List<com.fsd.admin.vo.AdminAnalyticsStationHourResponse.Row> buildStationHourRows(
            List<OrderEntity> orders) {
        java.util.Map<String, java.util.Map<Integer, Long>> byStationHour = new java.util.TreeMap<>();
        for (OrderEntity order : orders) {
            String station = order.getPickupNodeCode() == null || order.getPickupNodeCode().isBlank()
                    ? "未知"
                    : order.getPickupNodeCode();
            int hour = order.getCreatedAt() == null ? 0 : order.getCreatedAt().getHour();
            byStationHour.computeIfAbsent(station, key -> new java.util.TreeMap<>())
                    .merge(hour, 1L, Long::sum);
        }
        java.util.List<com.fsd.admin.vo.AdminAnalyticsStationHourResponse.Row> rows = new java.util.ArrayList<>();
        byStationHour.forEach((station, hours) -> hours.forEach((hour, count) -> rows.add(
                com.fsd.admin.vo.AdminAnalyticsStationHourResponse.Row.builder()
                        .station(station)
                        .hour(hour)
                        .orders(count)
                        .build())));
        return rows;
    }

    /**
     * P1-1：调度指标块。口径（页面与 {@code exportCsv("dispatch-metrics", ...)} 共用本方法，天然一致）：
     * <ul>
     *   <li>availableVehicles = 在线 && dispatchStatus=IDLE && SOC ≥ 最低可派线（补能策略出口）</li>
     *   <li>busyVehicles = dispatchStatus=BUSY；chargingVehicles = 运行态 ∈ {TO_CHARGING, CHARGING, WAIT_CHARGING}
     *       （8 态中的补能三态，来自 Redis 运行态，缺态的车不计入）</li>
     *   <li>manualPendingVehicles = 运行态含 MANUAL（手动接管）</li>
     *   <li>lowSocVehicles = 在线 && SOC &lt; 低电告警线——低 SOC 运力损失的代理口径（这些车即将退出可派集合）</li>
     *   <li>pendingOrders = 窗口内 status ∈ {CREATED, WAITING_DISPATCH}（未派 backlog）</li>
     *   <li>supplyDemandRatio = availableVehicles / max(pendingOrders, 1)，保留 2 位</li>
     * </ul>
     */
    /** 包级私有以便同包测试直测口径；页面与导出走同一方法，天然一致。 */
    com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse.DispatchMetrics buildDispatchMetrics(
            List<VehicleEntity> onlineFleet, List<OrderEntity> orders) {
        int minAssignableSoc = fleetChargePolicy.minAssignableSoc();
        int lowSocLine = fleetChargePolicy.lowSocThreshold();
        java.util.List<Long> vehicleIds = onlineFleet.stream().map(VehicleEntity::getId).toList();
        java.util.Map<Long, com.fsd.dispatch.fleet.model.FleetRuntime> runtimes =
                fleetRuntimeService.getBatch(vehicleIds);

        long available = 0L;
        long busy = 0L;
        long charging = 0L;
        long manual = 0L;
        long lowSoc = 0L;
        for (VehicleEntity vehicle : onlineFleet) {
            String dispatchStatus = vehicle.getDispatchStatus();
            Integer soc = vehicle.getBatteryLevel();
            if ("BUSY".equals(dispatchStatus)) {
                busy++;
            }
            if ("IDLE".equals(dispatchStatus) && soc != null && soc >= minAssignableSoc) {
                available++;
            }
            if (soc != null && soc < lowSocLine) {
                lowSoc++;
            }
            com.fsd.dispatch.fleet.model.FleetRuntime runtime = runtimes.get(vehicle.getId());
            String stage = runtime == null ? null : runtime.getRuntimeStage();
            if (stage != null) {
                if ("TO_CHARGING".equals(stage) || "CHARGING".equals(stage) || "WAIT_CHARGING".equals(stage)) {
                    charging++;
                }
                if (stage.contains("MANUAL")) {
                    manual++;
                }
            }
        }
        long pending = orders.stream()
                .filter(order -> "CREATED".equals(order.getStatus())
                        || "WAITING_DISPATCH".equals(order.getStatus()))
                .count();
        double ratio = pending <= 0 ? available : Math.round(available / (double) pending * 100D) / 100D;
        return com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse.DispatchMetrics.builder()
                .availableVehicles(available)
                .busyVehicles(busy)
                .chargingVehicles(charging)
                .manualPendingVehicles(manual)
                .lowSocVehicles(lowSoc)
                .pendingOrders(pending)
                .supplyDemandRatio(ratio)
                .build();
    }

    @Override
    public AdminAnalyticsExceptionResponse getExceptionAnalysis(String period, Long parkId) {
        String normalized = normalizePeriod(period);
        LocalDateTime start = rangeStart(normalized);
        List<DispatchExceptionRecordEntity> exceptions = exceptionRecordMapper.selectList(
                        new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                                .ge(DispatchExceptionRecordEntity::getOccurTime, start)
                                .orderByAsc(DispatchExceptionRecordEntity::getOccurTime))
                .stream()
                .filter(item -> adminParkScopeService.matchesOrder(item.getOrderId(), parkId))
                .toList();

        long total = exceptions.size();
        List<AdminAnalyticsTypeCount> typeDistribution = exceptions.stream()
                .collect(Collectors.groupingBy(DispatchExceptionRecordEntity::getExceptionType, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> AdminAnalyticsTypeCount.builder()
                        .type(entry.getKey())
                        .count(entry.getValue())
                        .ratio(total == 0 ? 0D : round1(entry.getValue() * 100.0 / total))
                        .build())
                .toList();

        List<AdminAnalyticsTrendPoint> trend = buildExceptionTrend(exceptions, normalized, start);

        double avgResolution = exceptions.stream()
                .filter(item -> item.getResolvedTime() != null && item.getOccurTime() != null)
                .mapToLong(item -> Duration.between(item.getOccurTime(), item.getResolvedTime()).toMinutes())
                .average()
                .orElse(0D);

        List<AdminAnalyticsTypeCount> rootCauseHints = typeDistribution.stream().limit(5).toList();

        return AdminAnalyticsExceptionResponse.builder()
                .period(normalized)
                .typeDistribution(typeDistribution)
                .exceptionTrend(trend)
                .avgResolutionMinutes(round1(avgResolution))
                .rootCauseHints(rootCauseHints)
                .build();
    }

    @Override
    public AdminAnalyticsDailySummaryResponse getDailySummary(LocalDate date, Long parkId) {
        LocalDate target = date == null ? LocalDate.now() : date;
        LocalDateTime dayStart = target.atStartOfDay();
        LocalDateTime dayEnd = target.plusDays(1).atStartOfDay();
        LocalDateTime prevStart = target.minusDays(1).atStartOfDay();
        LocalDateTime weekStart = target.minusDays(7).atStartOfDay();
        LocalDateTime weekEnd = target.minusDays(6).atStartOfDay();

        List<OrderEntity> todayOrders = filterOrdersByPark(loadOrdersBetween(dayStart, dayEnd), parkId);
        List<OrderEntity> yesterdayOrders = filterOrdersByPark(loadOrdersBetween(prevStart, dayStart), parkId);
        List<OrderEntity> weekAgoOrders = filterOrdersByPark(loadOrdersBetween(weekStart, weekEnd), parkId);

        long orderTotal = todayOrders.size();
        long orderCompleted = todayOrders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();
        List<DispatchTaskEntity> todayTasks = filterTasksByPark(loadTasksBetween(dayStart, dayEnd), parkId);
        long taskSuccess = todayTasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count();

        List<DispatchExceptionRecordEntity> todayExceptions = loadExceptionsBetween(dayStart, dayEnd).stream()
                .filter(item -> adminParkScopeService.matchesOrder(item.getOrderId(), parkId))
                .toList();
        long openCount = todayExceptions.stream().filter(e -> "OPEN".equals(e.getExceptionStatus())).count();
        long resolvedCount = todayExceptions.stream().filter(e -> !"OPEN".equals(e.getExceptionStatus())).count();

        double todayRate = orderTotal == 0 ? 0D : (double) orderCompleted / orderTotal;
        double yesterdayRate = yesterdayOrders.isEmpty() ? 0D
                : (double) yesterdayOrders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count()
                / yesterdayOrders.size();
        double weekRate = weekAgoOrders.isEmpty() ? 0D
                : (double) weekAgoOrders.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count()
                / weekAgoOrders.size();

        List<String> highlights = new ArrayList<>();
        if (openCount > 0) {
            highlights.add(String.format("当日产生 %d 条未关闭异常", openCount));
        }
        if (taskSuccess > 0) {
            highlights.add(String.format("完成任务 %d 个", taskSuccess));
        }
        if (orderCompleted > 0) {
            highlights.add(String.format("完成订单 %d 单，完成率 %.1f%%", orderCompleted, todayRate * 100));
        }
        if (highlights.isEmpty()) {
            highlights.add("当日运营平稳，暂无显著事件");
        }

        return AdminAnalyticsDailySummaryResponse.builder()
                .date(target.toString())
                .orderTotal(orderTotal)
                .orderCompleted(orderCompleted)
                .orderCompletionRate(round1(todayRate * 100))
                .taskTotal(todayTasks.size())
                .taskSuccess(taskSuccess)
                .openExceptionCount(openCount)
                .resolvedExceptionCount(resolvedCount)
                .dayOverDayOrderRate(round1((todayRate - yesterdayRate) * 100))
                .weekOverWeekOrderRate(round1((todayRate - weekRate) * 100))
                .highlightEvents(highlights)
                .build();
    }

    @Override
    public AdminAnalyticsChargingOverviewResponse getChargingOverview() {
        return getChargingOverview(null);
    }

    @Override
    public AdminAnalyticsChargingOverviewResponse getChargingOverview(Long parkId) {
        List<ChargingSessionEntity> activeSessions = chargingSessionMapper.selectList(
                new LambdaQueryWrapper<ChargingSessionEntity>()
                        .eq(ChargingSessionEntity::getDeleted, 0)
                        .eq(ChargingSessionEntity::getSessionStatus, ChargingSessionStatus.ACTIVE.name())
                        .eq(parkId != null, ChargingSessionEntity::getParkId, parkId)
                        .orderByDesc(ChargingSessionEntity::getStartTime));

        Map<Long, VehicleEntity> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0)
                        .eq(parkId != null, VehicleEntity::getParkId, parkId))
                .stream()
                .collect(Collectors.toMap(VehicleEntity::getId, Function.identity(), (a, b) -> a));
        Map<Long, ChargingPileEntity> piles = chargingPileMapper.selectList(new LambdaQueryWrapper<ChargingPileEntity>()
                        .eq(ChargingPileEntity::getDeleted, 0)
                        .eq(parkId != null, ChargingPileEntity::getParkId, parkId))
                .stream()
                .collect(Collectors.toMap(ChargingPileEntity::getId, Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        List<AdminAnalyticsChargingSessionItem> activeItems = activeSessions.stream()
                .map(session -> {
                    VehicleEntity vehicle = vehicles.get(session.getVehicleId());
                    ChargingPileEntity pile = piles.get(session.getChargingPileId());
                    Integer currentSoc = fleetRuntimeService.get(session.getVehicleId())
                            .map(runtime -> runtime.getSoc())
                            .orElse(session.getStartSoc());
                    return AdminAnalyticsChargingSessionItem.builder()
                            .sessionId(session.getId())
                            .vehicleId(session.getVehicleId())
                            .vehicleCode(vehicle == null ? String.valueOf(session.getVehicleId()) : vehicle.getVehicleCode())
                            .chargingPileId(session.getChargingPileId())
                            .pileCode(pile == null ? String.valueOf(session.getChargingPileId()) : pile.getPileCode())
                            .startSoc(session.getStartSoc())
                            .currentSoc(currentSoc)
                            .startTime(session.getStartTime())
                            .elapsedMinutes(Math.max(0, Duration.between(session.getStartTime(), now).toMinutes()))
                            .build();
                })
                .toList();

        long totalPiles = piles.size();
        long occupied = activeSessions.stream().map(ChargingSessionEntity::getChargingPileId).distinct().count();

        Page<ChargingSessionEntity> recentCompletedPage = chargingSessionMapper.selectPage(
                new Page<>(1, 20, false),
                new LambdaQueryWrapper<ChargingSessionEntity>()
                        .eq(ChargingSessionEntity::getDeleted, 0)
                        .eq(ChargingSessionEntity::getSessionStatus, ChargingSessionStatus.COMPLETED.name())
                        .eq(parkId != null, ChargingSessionEntity::getParkId, parkId)
                        .orderByDesc(ChargingSessionEntity::getEndTime));
        List<ChargingSessionEntity> recentCompleted = recentCompletedPage.getRecords();

        List<AdminAnalyticsChargingHistoryItem> history = recentCompleted.stream()
                .map(session -> toHistoryItem(session, vehicles, piles))
                .toList();

        double avgSpeed = history.stream()
                .map(AdminAnalyticsChargingHistoryItem::getChargeSpeedPerHour)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);

        List<BatterySwapSessionEntity> activeSwaps = batterySwapSessionMapper.selectList(
                new LambdaQueryWrapper<BatterySwapSessionEntity>()
                        .eq(BatterySwapSessionEntity::getStatus, "IN_PROGRESS"));
        Page<BatterySwapSessionEntity> completedSwapsPage = batterySwapSessionMapper.selectPage(
                new Page<>(1, 20, false),
                new LambdaQueryWrapper<BatterySwapSessionEntity>()
                        .eq(BatterySwapSessionEntity::getStatus, "COMPLETED")
                        .orderByDesc(BatterySwapSessionEntity::getFinishedAt));
        List<BatterySwapSessionEntity> completedSwaps = completedSwapsPage.getRecords();
        double chargeDuration = history.stream()
                .map(AdminAnalyticsChargingHistoryItem::getDurationMinutes)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        double swapDuration = completedSwaps.stream()
                .filter(s -> s.getStartedAt() != null && s.getFinishedAt() != null)
                .mapToDouble(s -> Duration.between(s.getStartedAt(), s.getFinishedAt()).toMinutes())
                .sum();

        return AdminAnalyticsChargingOverviewResponse.builder()
                .activeSessions(activeItems)
                .activeSessionCount(activeItems.size())
                .occupiedPileCount(occupied)
                .totalPileCount(totalPiles)
                .avgChargeSpeedPerHour(round1(avgSpeed))
                .activeSwapSessionCount(activeSwaps.size())
                .totalChargeDurationMinutes(round1(chargeDuration))
                .totalSwapDurationMinutes(round1(swapDuration))
                .recentHistory(history)
                .build();
    }

    @Override
    public String exportCsv(String dataset, String period, Long parkId) {
        String normalized = normalizePeriod(period);
        AtomicInteger rows = new AtomicInteger();
        String datasetTag = dataset == null ? "unknown" : dataset.toLowerCase(Locale.ROOT);
        try {
            return doExportCsv(dataset, normalized, parkId, rows, datasetTag);
        } catch (BusinessException ex) {
            meterRegistry.counter("dispatchflow.export.requests",
                    "dataset", datasetTag, "result", ex.getCode()).increment();
            throw ex;
        } catch (Exception ex) {
            meterRegistry.counter("dispatchflow.export.requests",
                    "dataset", datasetTag, "result", "error").increment();
            throw ex;
        } finally {
            if (rows.get() > 0) {
                meterRegistry.counter("dispatchflow.export.rows", "dataset", datasetTag).increment(rows.get());
            }
            meterRegistry.counter("dispatchflow.export.requests",
                    "dataset", datasetTag, "result", "completed").increment();
        }
    }

    private String doExportCsv(String dataset, String normalized, Long parkId,
                               AtomicInteger rows, String datasetTag) {
        StringBuilder sb = new StringBuilder();
        switch (dataset == null ? "" : dataset.toLowerCase(Locale.ROOT)) {
            case "orders" -> {
                sb.append("orderNo,status,priority,createdAt\n");
                filterOrdersByPark(loadOrdersSince(rangeStart(normalized)), parkId).forEach(order ->
                        appendRow(sb, rows, () -> sb.append(csv(order.getOrderNo())).append(',')
                                .append(csv(order.getStatus())).append(',')
                                .append(csv(order.getPriority())).append(',')
                                .append(order.getCreatedAt()).append('\n')));
            }
            case "tasks" -> {
                sb.append("taskNo,status,orderId,vehicleId,createdAt,finishTime\n");
                filterTasksByPark(loadTasksSince(rangeStart(normalized)), parkId).forEach(task ->
                        appendRow(sb, rows, () -> sb.append(csv(task.getTaskNo())).append(',')
                                .append(csv(task.getStatus())).append(',')
                                .append(task.getOrderId()).append(',')
                                .append(task.getVehicleId()).append(',')
                                .append(task.getCreatedAt()).append(',')
                                .append(task.getFinishTime()).append('\n')));
            }
            case "exceptions" -> {
                sb.append("exceptionType,status,severity,occurTime,resolvedTime\n");
                exceptionRecordMapper.selectList(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                                .ge(DispatchExceptionRecordEntity::getOccurTime, rangeStart(normalized))
                                .orderByDesc(DispatchExceptionRecordEntity::getOccurTime))
                        .stream()
                        .filter(item -> adminParkScopeService.matchesOrder(item.getOrderId(), parkId))
                        .forEach(item -> appendRow(sb, rows, () -> sb.append(csv(item.getExceptionType())).append(',')
                                .append(csv(item.getExceptionStatus())).append(',')
                                .append(csv(item.getSeverity())).append(',')
                                .append(item.getOccurTime()).append(',')
                                .append(item.getResolvedTime()).append('\n')));
            }
            case "vehicles" -> {
                sb.append("vehicleCode,vehicleName,onlineStatus,dispatchStatus,batteryLevel\n");
                vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                                .eq(VehicleEntity::getDeleted, 0)
                                .orderByAsc(VehicleEntity::getVehicleCode))
                        .stream()
                        .filter(vehicle -> matchesVehicleEntityPark(vehicle, parkId))
                        .forEach(vehicle -> appendRow(sb, rows, () -> sb.append(csv(vehicle.getVehicleCode())).append(',')
                                .append(csv(vehicle.getVehicleName())).append(',')
                                .append(csv(vehicle.getOnlineStatus())).append(',')
                                .append(csv(vehicle.getDispatchStatus())).append(',')
                                .append(vehicle.getBatteryLevel()).append('\n')));
            }
            case "dispatch-metrics" -> {
                // P1-1：与 getEfficiency 的 dispatchMetrics 共用 buildDispatchMetrics——页面与导出同口径
                String normalizedPeriod = normalized;
                List<VehicleEntity> fleet = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                                .eq(VehicleEntity::getDeleted, 0)
                                .eq(VehicleEntity::getOnlineStatus, "ONLINE"))
                        .stream()
                        .filter(vehicle -> matchesVehicleEntityPark(vehicle, parkId))
                        .toList();
                var metrics = buildDispatchMetrics(fleet,
                        filterOrdersByPark(loadOrdersSince(rangeStart(normalizedPeriod)), parkId));
                sb.append("metric,value\n");
                appendRow(sb, rows, () -> sb.append("availableVehicles,").append(metrics.getAvailableVehicles()).append('\n'));
                appendRow(sb, rows, () -> sb.append("busyVehicles,").append(metrics.getBusyVehicles()).append('\n'));
                appendRow(sb, rows, () -> sb.append("chargingVehicles,").append(metrics.getChargingVehicles()).append('\n'));
                appendRow(sb, rows, () -> sb.append("manualPendingVehicles,").append(metrics.getManualPendingVehicles()).append('\n'));
                appendRow(sb, rows, () -> sb.append("lowSocVehicles,").append(metrics.getLowSocVehicles()).append('\n'));
                appendRow(sb, rows, () -> sb.append("pendingOrders,").append(metrics.getPendingOrders()).append('\n'));
                appendRow(sb, rows, () -> sb.append("supplyDemandRatio,").append(metrics.getSupplyDemandRatio()).append('\n'));
            }
            case "station-hourly" -> {
                // P1-1：与页面 /station-hourly 共用 buildStationHourRows——页面与导出同口径
                com.fsd.admin.vo.AdminAnalyticsStationHourResponse demand =
                        getStationHourlyDemand(normalized, parkId);
                sb.append("station,hour,orders\n");
                for (com.fsd.admin.vo.AdminAnalyticsStationHourResponse.Row row : demand.getRows()) {
                    appendRow(sb, rows, () -> sb.append(csv(row.getStation())).append(',')
                            .append(row.getHour()).append(',')
                            .append(row.getOrders()).append('\n'));
                }
            }
            default -> throw new IllegalArgumentException("Unsupported dataset: " + dataset);
        }
        return sb.toString();
    }

    /** 追加一行并执行导出行数上限检查（路线图 Phase 5）。 */
    private void appendRow(StringBuilder sb, AtomicInteger rows, Runnable appender) {
        if (rows.incrementAndGet() > exportMaxRows) {
            throw new BusinessException("EXPORT_ROW_LIMIT_EXCEEDED",
                    "导出数据量超过上限（" + exportMaxRows + " 行），请缩小时间范围，或使用报表计划与历史报表下载");
        }
        appender.run();
    }

    private AdminAnalyticsChargingHistoryItem toHistoryItem(ChargingSessionEntity session,
                                                              Map<Long, VehicleEntity> vehicles,
                                                              Map<Long, ChargingPileEntity> piles) {
        VehicleEntity vehicle = vehicles.get(session.getVehicleId());
        ChargingPileEntity pile = piles.get(session.getChargingPileId());
        long minutes = session.getStartTime() != null && session.getEndTime() != null
                ? Duration.between(session.getStartTime(), session.getEndTime()).toMinutes()
                : 0L;
        Double speed = null;
        if (minutes > 0 && session.getStartSoc() != null && session.getEndSoc() != null) {
            speed = round1((session.getEndSoc() - session.getStartSoc()) * 60.0 / minutes);
        }
        return AdminAnalyticsChargingHistoryItem.builder()
                .sessionId(session.getId())
                .vehicleId(session.getVehicleId())
                .vehicleCode(vehicle == null ? String.valueOf(session.getVehicleId()) : vehicle.getVehicleCode())
                .pileCode(pile == null ? String.valueOf(session.getChargingPileId()) : pile.getPileCode())
                .startSoc(session.getStartSoc())
                .endSoc(session.getEndSoc())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .durationMinutes(minutes)
                .chargeSpeedPerHour(speed)
                .build();
    }

    private List<AdminAnalyticsTrendPoint> buildOrderTrend(List<OrderEntity> orders, String period, LocalDateTime start) {
        Map<String, List<OrderEntity>> grouped = groupByBucket(orders, OrderEntity::getCreatedAt, period, start);
        List<AdminAnalyticsTrendPoint> trend = new ArrayList<>();
        grouped.forEach((label, bucket) -> {
            long total = bucket.size();
            long completed = bucket.stream().filter(o -> "COMPLETED".equals(o.getStatus())).count();
            trend.add(AdminAnalyticsTrendPoint.builder()
                    .label(label)
                    .totalCount(total)
                    .completedCount(completed)
                    .completionRate(round1(total == 0 ? 0D : completed * 100.0 / total))
                    .build());
        });
        return trend;
    }

    private List<AdminAnalyticsTrendPoint> buildExceptionTrend(List<DispatchExceptionRecordEntity> exceptions,
                                                                 String period,
                                                                 LocalDateTime start) {
        Map<String, List<DispatchExceptionRecordEntity>> grouped = groupByBucket(
                exceptions, DispatchExceptionRecordEntity::getOccurTime, period, start);
        List<AdminAnalyticsTrendPoint> trend = new ArrayList<>();
        grouped.forEach((label, bucket) -> trend.add(AdminAnalyticsTrendPoint.builder()
                .label(label)
                .totalCount(bucket.size())
                .completedCount(bucket.stream().filter(e -> !"OPEN".equals(e.getExceptionStatus())).count())
                .completionRate(0D)
                .build()));
        return trend;
    }

    private List<AdminAnalyticsHourlyPoint> buildPeakHours(List<OrderEntity> orders, List<DispatchTaskEntity> tasks) {
        Map<Integer, AdminAnalyticsHourlyPoint> points = new LinkedHashMap<>();
        for (int hour = 0; hour < 24; hour++) {
            points.put(hour, AdminAnalyticsHourlyPoint.builder().hour(hour).orderCount(0).taskCount(0).build());
        }
        orders.forEach(order -> {
            if (order.getCreatedAt() == null) {
                return;
            }
            int hour = order.getCreatedAt().getHour();
            AdminAnalyticsHourlyPoint point = points.get(hour);
            point.setOrderCount(point.getOrderCount() + 1);
        });
        tasks.forEach(task -> {
            if (task.getCreatedAt() == null) {
                return;
            }
            int hour = task.getCreatedAt().getHour();
            AdminAnalyticsHourlyPoint point = points.get(hour);
            point.setTaskCount(point.getTaskCount() + 1);
        });
        return points.values().stream()
                .sorted(Comparator.comparingInt(AdminAnalyticsHourlyPoint::getHour))
                .toList();
    }

    private <T> Map<String, List<T>> groupByBucket(List<T> items,
                                                   Function<T, LocalDateTime> timeExtractor,
                                                   String period,
                                                   LocalDateTime start) {
        Map<String, List<T>> grouped = new LinkedHashMap<>();
        long bucketCount = "month".equals(period) ? 30 : ("week".equals(period) ? 7 : 1);
        LocalDate startDate = start.toLocalDate();
        for (int i = 0; i < bucketCount; i++) {
            LocalDate day = startDate.plusDays(i);
            if (day.isAfter(LocalDate.now())) {
                break;
            }
            grouped.put(day.format(DAY_FMT), new ArrayList<>());
        }
        for (T item : items) {
            LocalDateTime time = timeExtractor.apply(item);
            if (time == null) {
                continue;
            }
            String key = time.toLocalDate().format(DAY_FMT);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    private List<OrderEntity> loadOrdersSince(LocalDateTime start) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getDeleted, 0)
                .ge(OrderEntity::getCreatedAt, start)
                .orderByAsc(OrderEntity::getCreatedAt));
    }

    private List<OrderEntity> loadOrdersBetween(LocalDateTime start, LocalDateTime end) {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderEntity>()
                .eq(OrderEntity::getDeleted, 0)
                .ge(OrderEntity::getCreatedAt, start)
                .lt(OrderEntity::getCreatedAt, end));
    }

    private List<DispatchTaskEntity> loadTasksSince(LocalDateTime start) {
        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .ge(DispatchTaskEntity::getCreatedAt, start)
                .orderByAsc(DispatchTaskEntity::getCreatedAt));
    }

    private List<DispatchTaskEntity> loadTasksBetween(LocalDateTime start, LocalDateTime end) {
        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .ge(DispatchTaskEntity::getCreatedAt, start)
                .lt(DispatchTaskEntity::getCreatedAt, end));
    }

    private List<DispatchExceptionRecordEntity> loadExceptionsBetween(LocalDateTime start, LocalDateTime end) {
        return exceptionRecordMapper.selectList(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                .ge(DispatchExceptionRecordEntity::getOccurTime, start)
                .lt(DispatchExceptionRecordEntity::getOccurTime, end));
    }

    private String normalizePeriod(String period) {
        if (period == null || period.isBlank()) {
            return "week";
        }
        String normalized = period.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "day", "week", "month" -> normalized;
            default -> "week";
        };
    }

    private LocalDateTime rangeStart(String period) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "day" -> today.atStartOfDay();
            case "month" -> today.minusDays(29).atStartOfDay();
            default -> today.minusDays(6).atStartOfDay();
        };
    }

    @Override
    public List<AdminAnalyticsParkCompareItem> getParkComparison(String period) {
        LocalDateTime start = rangeStart(normalizePeriod(period));
        List<OrderEntity> orders = loadOrdersSince(start);
        List<DispatchTaskEntity> tasks = loadTasksSince(start);
        List<DispatchExceptionRecordEntity> exceptions = exceptionRecordMapper.selectList(
                new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                        .ge(DispatchExceptionRecordEntity::getCreatedAt, start));

        List<ParkEntity> parks = parkMapper.selectList(new LambdaQueryWrapper<ParkEntity>()
                .eq(ParkEntity::getDeleted, 0)
                .eq(ParkEntity::getStatus, "ACTIVE"));

        return parks.stream()
                .map(park -> AdminAnalyticsParkCompareItem.builder()
                        .parkId(park.getId())
                        .parkName(park.getParkName())
                        .orderCount(orders.stream()
                                .filter(o -> park.getId().equals(o.getParkId()))
                                .count())
                        .taskSuccessCount(tasks.stream()
                                .filter(t -> park.getId().equals(resolveTaskParkId(t, orders)))
                                .filter(t -> "SUCCESS".equals(t.getStatus()))
                                .count())
                        .openExceptionCount(exceptions.stream()
                                .filter(ex -> "OPEN".equalsIgnoreCase(ex.getExceptionStatus()))
                                .filter(ex -> {
                                    OrderEntity order = orders.stream()
                                            .filter(o -> o.getId().equals(ex.getOrderId()))
                                            .findFirst()
                                            .orElse(null);
                                    return order != null && park.getId().equals(order.getParkId());
                                })
                                .count())
                        .build())
                .sorted(Comparator.comparingLong(AdminAnalyticsParkCompareItem::getOrderCount).reversed())
                .toList();
    }

    private Long resolveTaskParkId(DispatchTaskEntity task, List<OrderEntity> orders) {
        if (task.getOrderId() == null) {
            return null;
        }
        return orders.stream()
                .filter(o -> o.getId().equals(task.getOrderId()))
                .map(OrderEntity::getParkId)
                .findFirst()
                .orElse(null);
    }

    private List<OrderEntity> filterOrdersByPark(List<OrderEntity> orders, Long parkId) {
        if (parkId == null) {
            return orders;
        }
        return orders.stream()
                .filter(order -> parkId.equals(order.getParkId()))
                .toList();
    }

    private List<DispatchTaskEntity> filterTasksByPark(List<DispatchTaskEntity> tasks, Long parkId) {
        if (parkId == null) {
            return tasks;
        }
        return tasks.stream()
                .filter(task -> adminParkScopeService.matchesOrder(task.getOrderId(), parkId))
                .toList();
    }

    private boolean matchesVehicleEntityPark(VehicleEntity vehicle, Long parkId) {
        if (parkId == null) {
            return true;
        }
        return adminParkScopeService.matchesVehicle(
                VehicleAdminListItemResponse.builder()
                        .vehicleId(vehicle.getId())
                        .parkId(vehicle.getParkId())
                        .currentOrderId(vehicle.getCurrentOrderId())
                        .currentTaskId(vehicle.getCurrentTaskId())
                        .build(),
                parkId);
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    @Override
    public AdminAnalyticsChainKpiResponse getChainKpi(String period, Long parkId) {
        String normalized = normalizePeriod(period);
        LocalDateTime start = rangeStart(normalized);
        List<DispatchTaskEntity> tasks = filterTasksByPark(loadTasksSince(start), parkId);
        List<DispatchTaskEntity> success = tasks.stream()
                .filter(t -> "SUCCESS".equals(t.getStatus()))
                .filter(t -> t.getStartTime() != null && t.getFinishTime() != null)
                .toList();
        double avgCompletion = success.stream()
                .mapToLong(t -> Duration.between(t.getStartTime(), t.getFinishTime()).toMinutes())
                .average()
                .orElse(0D);
        List<Long> waitMinutes = tasks.stream()
                .filter(t -> t.getCreatedAt() != null && t.getAssignTime() != null)
                .map(t -> Duration.between(t.getCreatedAt(), t.getAssignTime()).toMinutes())
                .sorted()
                .toList();
        double p50 = percentile(waitMinutes, 50);
        double p90 = percentile(waitMinutes, 90);
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), LocalDate.now()) + 1);
        long vehicleCount = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0))
                .stream()
                .filter(v -> matchesVehicleEntityPark(v, parkId))
                .count();
        long successCount = success.size();
        double tasksPerVehiclePerDay = vehicleCount <= 0 ? 0D : (double) successCount / vehicleCount / days;
        return AdminAnalyticsChainKpiResponse.builder()
                .period(normalized)
                .parkId(parkId)
                .avgCompletionMinutes(round1(avgCompletion))
                .waitP50Minutes(round1(p50))
                .waitP90Minutes(round1(p90))
                .tasksPerVehiclePerDay(round1(tasksPerVehiclePerDay))
                .build();
    }

    @Override
    public AdminPeakCompareResponse getPeakCompare(String period, Long parkId) {
        String normalized = normalizePeriod(period);
        LocalDateTime start = rangeStart(normalized);
        List<DispatchTaskEntity> tasks = filterTasksByPark(loadTasksSince(start), parkId);
        return AdminPeakCompareResponse.builder()
                .normalMode(buildChainKpiFromTasks(tasks, normalized, parkId, "NORMAL"))
                .peakMode(buildChainKpiFromTasks(tasks, normalized, parkId, "PEAK"))
                .build();
    }

    private AdminAnalyticsChainKpiResponse buildChainKpiFromTasks(List<DispatchTaskEntity> tasks,
                                                                  String period,
                                                                  Long parkId,
                                                                  String peakMode) {
        List<DispatchTaskEntity> filtered = tasks.stream()
                .filter(t -> peakMode.equalsIgnoreCase(t.getPeakModeAtFinish()))
                .filter(t -> "SUCCESS".equals(t.getStatus()))
                .toList();
        double avgCompletion = filtered.stream()
                .filter(t -> t.getStartTime() != null && t.getFinishTime() != null)
                .mapToLong(t -> Duration.between(t.getStartTime(), t.getFinishTime()).toMinutes())
                .average()
                .orElse(0D);
        List<Long> waitMinutes = tasks.stream()
                .filter(t -> peakMode.equalsIgnoreCase(t.getPeakModeAtFinish()))
                .filter(t -> t.getCreatedAt() != null && t.getAssignTime() != null)
                .map(t -> Duration.between(t.getCreatedAt(), t.getAssignTime()).toMinutes())
                .sorted()
                .toList();
        long vehicleCount = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0))
                .stream()
                .filter(v -> matchesVehicleEntityPark(v, parkId))
                .count();
        long days = Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(rangeStart(period).toLocalDate(), LocalDate.now()) + 1);
        double tasksPerVehiclePerDay = vehicleCount <= 0 ? 0D : (double) filtered.size() / vehicleCount / days;
        return AdminAnalyticsChainKpiResponse.builder()
                .period(period)
                .parkId(parkId)
                .avgCompletionMinutes(round1(avgCompletion))
                .waitP50Minutes(round1(percentile(waitMinutes, 50)))
                .waitP90Minutes(round1(percentile(waitMinutes, 90)))
                .tasksPerVehiclePerDay(round1(tasksPerVehiclePerDay))
                .build();
    }

    @Override
    public AdminAnalyticsEnergyForecastResponse getEnergyForecast(LocalDate date, Long parkId) {
        LocalDate forecastDate = date == null ? LocalDate.now() : date;
        // 与既有 analytics 口径一致：parkId 为空时取默认园区（单园区部署下即唯一园区）
        Long resolvedParkId = parkId != null ? parkId : adminParkScopeService.resolveDefaultParkId();
        List<EnergyForecastService.StationHourlyProfile> profiles = resolvedParkId == null
                ? List.of()
                : energyForecastService.parkHourlyProfiles(forecastDate, resolvedParkId);

        double threshold = energyForecastProperties.getPressureThreshold();
        List<AdminAnalyticsEnergyForecastStationItem> stations = profiles.stream()
                .map(profile -> toEnergyForecastStation(profile, threshold))
                .sorted(Comparator.comparing(AdminAnalyticsEnergyForecastStationItem::getStationId,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        return AdminAnalyticsEnergyForecastResponse.builder()
                .enabled(energyForecastService.isEnabled())
                .pressureThreshold(threshold)
                .maxDataAgeHours(energyForecastProperties.getMaxDataAgeHours())
                .forecastDate(forecastDate)
                .parkId(resolvedParkId)
                .serverTime(LocalDateTime.now())
                .stationCount(stations.size())
                .anyData(!stations.isEmpty())
                .anyStale(stations.stream().anyMatch(AdminAnalyticsEnergyForecastStationItem::isStale))
                .stations(stations)
                .build();
    }

    private static AdminAnalyticsEnergyForecastStationItem toEnergyForecastStation(
            EnergyForecastService.StationHourlyProfile profile, double pressureThreshold) {
        List<AdminAnalyticsEnergyForecastHourPoint> hours = profile.hours().stream()
                .map(point -> AdminAnalyticsEnergyForecastHourPoint.builder()
                        .hourOfDay(point.hourOfDay())
                        .demandP50(point.demandP50())
                        .demandP90(point.demandP90())
                        .pressureP95(point.pressureP95())
                        .build())
                .toList();

        BigDecimal peakPressure = hours.stream()
                .map(AdminAnalyticsEnergyForecastHourPoint::getPressureP95)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        int peakHour = hours.stream()
                .filter(point -> point.getPressureP95() != null
                        && peakPressure.compareTo(point.getPressureP95()) == 0)
                .mapToInt(AdminAnalyticsEnergyForecastHourPoint::getHourOfDay)
                .findFirst()
                .orElse(0);
        BigDecimal peakP90 = hours.stream()
                .map(AdminAnalyticsEnergyForecastHourPoint::getDemandP90)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);

        return AdminAnalyticsEnergyForecastStationItem.builder()
                .parkId(profile.parkId())
                .stationId(profile.stationId())
                .stationCode(profile.stationCode())
                .modelVersion(profile.modelVersion())
                .generatedAt(profile.generatedAt())
                .stale(profile.stale())
                .sampleCount(profile.sampleCount())
                .hourCount(hours.size())
                .peakPressureP95(peakPressure)
                .peakHourOfDay(peakHour)
                .peakDemandP90(peakP90)
                .pressureThresholdExceeded(peakPressure.doubleValue() >= pressureThreshold)
                .hours(hours)
                .build();
    }

    @Override
    public byte[] exportPdf(LocalDate date, Long parkId) {
        LocalDate target = date == null ? LocalDate.now() : date;
        AdminAnalyticsDailySummaryResponse summary = getDailySummary(target, parkId);
        AdminAnalyticsEfficiencyResponse efficiency = getEfficiency("day", parkId);
        AdminAnalyticsChainKpiResponse chain = getChainKpi("day", parkId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
            document.add(new Paragraph("DispatchFlow 运营日报", titleFont));
            document.add(new Paragraph("日期: " + summary.getDate(), bodyFont));
            document.add(new Paragraph("订单完成: " + summary.getOrderCompleted() + "/" + summary.getOrderTotal()
                    + " (" + summary.getOrderCompletionRate() + "%)", bodyFont));
            document.add(new Paragraph("平均任务时长: " + efficiency.getAvgTaskDurationMinutes() + " 分钟", bodyFont));
            document.add(new Paragraph("车辆利用率: " + efficiency.getVehicleUtilizationRate() + "%", bodyFont));
            document.add(new Paragraph("链路 KPI — 完成均时: " + chain.getAvgCompletionMinutes()
                    + " 分, 等待 P50/P90: " + chain.getWaitP50Minutes() + "/" + chain.getWaitP90Minutes()
                    + " 分, 单车日均: " + chain.getTasksPerVehiclePerDay(), bodyFont));
            document.close();
            return out.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("PDF export failed", ex);
        }
    }

    private static double percentile(List<Long> sorted, int pct) {
        if (sorted.isEmpty()) {
            return 0D;
        }
        int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}

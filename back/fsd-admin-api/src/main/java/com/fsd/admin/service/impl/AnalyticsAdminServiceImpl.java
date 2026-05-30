package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.service.AnalyticsAdminService;
import com.fsd.admin.service.AdminParkScopeService;
import com.fsd.admin.vo.AdminAnalyticsChargingHistoryItem;
import com.fsd.admin.vo.AdminAnalyticsChargingOverviewResponse;
import com.fsd.admin.vo.AdminAnalyticsChargingSessionItem;
import com.fsd.admin.vo.AdminAnalyticsDailySummaryResponse;
import com.fsd.admin.vo.AdminAnalyticsEfficiencyResponse;
import com.fsd.admin.vo.AdminAnalyticsExceptionResponse;
import com.fsd.admin.vo.AdminAnalyticsHourlyPoint;
import com.fsd.admin.vo.AdminAnalyticsTrendPoint;
import com.fsd.admin.vo.AdminAnalyticsParkCompareItem;
import com.fsd.admin.vo.AdminAnalyticsTypeCount;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.common.enums.ChargingSessionStatus;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsAdminServiceImpl implements AnalyticsAdminService {

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private final OrderMapper orderMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final ChargingSessionMapper chargingSessionMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final VehicleMapper vehicleMapper;
    private final FleetRuntimeService fleetRuntimeService;
    private final ParkMapper parkMapper;
    private final AdminParkScopeService adminParkScopeService;

    public AnalyticsAdminServiceImpl(OrderMapper orderMapper,
                                     DispatchTaskMapper dispatchTaskMapper,
                                     DispatchExceptionRecordMapper exceptionRecordMapper,
                                     ChargingSessionMapper chargingSessionMapper,
                                     ChargingPileMapper chargingPileMapper,
                                     VehicleMapper vehicleMapper,
                                     FleetRuntimeService fleetRuntimeService,
                                     ParkMapper parkMapper,
                                     AdminParkScopeService adminParkScopeService) {
        this.orderMapper = orderMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.chargingSessionMapper = chargingSessionMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.vehicleMapper = vehicleMapper;
        this.fleetRuntimeService = fleetRuntimeService;
        this.parkMapper = parkMapper;
        this.adminParkScopeService = adminParkScopeService;
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
        long onlineVehicles = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0)
                        .eq(VehicleEntity::getOnlineStatus, "ONLINE"))
                .stream()
                .filter(vehicle -> matchesVehicleEntityPark(vehicle, parkId))
                .count();
        double utilization = onlineVehicles <= 0 ? 0D : (double) busyTasks / onlineVehicles;

        List<AdminAnalyticsHourlyPoint> peakHours = buildPeakHours(orders, tasks);

        return AdminAnalyticsEfficiencyResponse.builder()
                .period(normalized)
                .orderCompletionTrend(trend)
                .avgTaskDurationMinutes(round1(avgDuration))
                .vehicleUtilizationRate(round1(utilization * 100))
                .peakHours(peakHours)
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
        List<ChargingSessionEntity> activeSessions = chargingSessionMapper.selectList(
                new LambdaQueryWrapper<ChargingSessionEntity>()
                        .eq(ChargingSessionEntity::getDeleted, 0)
                        .eq(ChargingSessionEntity::getSessionStatus, ChargingSessionStatus.ACTIVE.name())
                        .orderByDesc(ChargingSessionEntity::getStartTime));

        Map<Long, VehicleEntity> vehicles = vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                        .eq(VehicleEntity::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(VehicleEntity::getId, Function.identity(), (a, b) -> a));
        Map<Long, ChargingPileEntity> piles = chargingPileMapper.selectList(new LambdaQueryWrapper<ChargingPileEntity>()
                        .eq(ChargingPileEntity::getDeleted, 0))
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

        List<ChargingSessionEntity> recentCompleted = chargingSessionMapper.selectList(
                new LambdaQueryWrapper<ChargingSessionEntity>()
                        .eq(ChargingSessionEntity::getDeleted, 0)
                        .eq(ChargingSessionEntity::getSessionStatus, ChargingSessionStatus.COMPLETED.name())
                        .orderByDesc(ChargingSessionEntity::getEndTime)
                        .last("LIMIT 20"));

        List<AdminAnalyticsChargingHistoryItem> history = recentCompleted.stream()
                .map(session -> toHistoryItem(session, vehicles, piles))
                .toList();

        double avgSpeed = history.stream()
                .map(AdminAnalyticsChargingHistoryItem::getChargeSpeedPerHour)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0D);

        return AdminAnalyticsChargingOverviewResponse.builder()
                .activeSessions(activeItems)
                .activeSessionCount(activeItems.size())
                .occupiedPileCount(occupied)
                .totalPileCount(totalPiles)
                .avgChargeSpeedPerHour(round1(avgSpeed))
                .recentHistory(history)
                .build();
    }

    @Override
    public String exportCsv(String dataset, String period) {
        String normalized = normalizePeriod(period);
        StringBuilder sb = new StringBuilder();
        switch (dataset == null ? "" : dataset.toLowerCase()) {
            case "orders" -> {
                sb.append("orderNo,status,priority,createdAt\n");
                loadOrdersSince(rangeStart(normalized)).forEach(order ->
                        sb.append(csv(order.getOrderNo())).append(',')
                                .append(csv(order.getStatus())).append(',')
                                .append(csv(order.getPriority())).append(',')
                                .append(order.getCreatedAt()).append('\n'));
            }
            case "tasks" -> {
                sb.append("taskNo,status,orderId,vehicleId,createdAt,finishTime\n");
                loadTasksSince(rangeStart(normalized)).forEach(task ->
                        sb.append(csv(task.getTaskNo())).append(',')
                                .append(csv(task.getStatus())).append(',')
                                .append(task.getOrderId()).append(',')
                                .append(task.getVehicleId()).append(',')
                                .append(task.getCreatedAt()).append(',')
                                .append(task.getFinishTime()).append('\n'));
            }
            case "exceptions" -> {
                sb.append("exceptionType,status,severity,occurTime,resolvedTime\n");
                exceptionRecordMapper.selectList(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                                .ge(DispatchExceptionRecordEntity::getOccurTime, rangeStart(normalized))
                                .orderByDesc(DispatchExceptionRecordEntity::getOccurTime))
                        .forEach(item -> sb.append(csv(item.getExceptionType())).append(',')
                                .append(csv(item.getExceptionStatus())).append(',')
                                .append(csv(item.getSeverity())).append(',')
                                .append(item.getOccurTime()).append(',')
                                .append(item.getResolvedTime()).append('\n'));
            }
            case "vehicles" -> {
                sb.append("vehicleCode,vehicleName,onlineStatus,dispatchStatus,batteryLevel\n");
                vehicleMapper.selectList(new LambdaQueryWrapper<VehicleEntity>()
                                .eq(VehicleEntity::getDeleted, 0)
                                .orderByAsc(VehicleEntity::getVehicleCode))
                        .forEach(vehicle -> sb.append(csv(vehicle.getVehicleCode())).append(',')
                                .append(csv(vehicle.getVehicleName())).append(',')
                                .append(csv(vehicle.getOnlineStatus())).append(',')
                                .append(csv(vehicle.getDispatchStatus())).append(',')
                                .append(vehicle.getBatteryLevel()).append('\n'));
            }
            default -> throw new IllegalArgumentException("Unsupported dataset: " + dataset);
        }
        return sb.toString();
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
        return switch (period.toLowerCase()) {
            case "day", "week", "month" -> period.toLowerCase();
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
}

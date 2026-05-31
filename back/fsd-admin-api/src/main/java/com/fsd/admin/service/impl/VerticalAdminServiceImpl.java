package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminAutomationRuleUpsertRequest;
import com.fsd.admin.dto.AdminDispatchRouteUpsertRequest;
import com.fsd.admin.dto.AdminPeakModeUpsertRequest;
import com.fsd.admin.service.VerticalAdminService;
import com.fsd.admin.vo.AdminAutomationRuleAuditResponse;
import com.fsd.admin.vo.AdminAutomationRuleResponse;
import com.fsd.admin.vo.AdminDispatchRouteResponse;
import com.fsd.admin.vo.AdminHubOverviewResponse;
import com.fsd.admin.vo.AdminHubQueuedTaskResponse;
import com.fsd.admin.vo.AdminHubStationStatusResponse;
import com.fsd.admin.vo.AdminPeakModeResponse;
import com.fsd.admin.vo.AdminRouteStationResponse;
import com.fsd.common.enums.StationType;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchAutomationRuleAuditEntity;
import com.fsd.dispatch.entity.DispatchAutomationRuleEntity;
import com.fsd.dispatch.entity.DispatchRouteEntity;
import com.fsd.dispatch.entity.DispatchRouteStationEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.PeakModeStateEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.DispatchAutomationRuleAuditMapper;
import com.fsd.dispatch.mapper.DispatchAutomationRuleMapper;
import com.fsd.dispatch.mapper.DispatchRouteMapper;
import com.fsd.dispatch.mapper.DispatchRouteStationMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.StationMapper;
import com.fsd.dispatch.service.DispatchRouteService;
import com.fsd.dispatch.service.HubCapacityService;
import com.fsd.dispatch.service.PeakModeService;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerticalAdminServiceImpl implements VerticalAdminService {

    private static final Set<String> HUB_TYPES = Set.of(
            StationType.HUB.name(), StationType.BUFFER.name(), StationType.MOTHERSHIP.name());
    private static final Set<String> QUEUED_STATUSES = Set.of("PENDING", "MANUAL_PENDING");

    private final DispatchRouteMapper dispatchRouteMapper;
    private final DispatchRouteStationMapper dispatchRouteStationMapper;
    private final DispatchRouteService dispatchRouteService;
    private final PeakModeService peakModeService;
    private final StationMapper stationMapper;
    private final HubCapacityService hubCapacityService;
    private final OrderMapper orderMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchAutomationRuleMapper automationRuleMapper;
    private final DispatchAutomationRuleAuditMapper automationRuleAuditMapper;

    public VerticalAdminServiceImpl(DispatchRouteMapper dispatchRouteMapper,
                                    DispatchRouteStationMapper dispatchRouteStationMapper,
                                    DispatchRouteService dispatchRouteService,
                                    PeakModeService peakModeService,
                                    StationMapper stationMapper,
                                    HubCapacityService hubCapacityService,
                                    OrderMapper orderMapper,
                                    DispatchTaskMapper dispatchTaskMapper,
                                    DispatchAutomationRuleMapper automationRuleMapper,
                                    DispatchAutomationRuleAuditMapper automationRuleAuditMapper) {
        this.dispatchRouteMapper = dispatchRouteMapper;
        this.dispatchRouteStationMapper = dispatchRouteStationMapper;
        this.dispatchRouteService = dispatchRouteService;
        this.peakModeService = peakModeService;
        this.stationMapper = stationMapper;
        this.hubCapacityService = hubCapacityService;
        this.orderMapper = orderMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.automationRuleMapper = automationRuleMapper;
        this.automationRuleAuditMapper = automationRuleAuditMapper;
    }

    @Override
    public List<AdminDispatchRouteResponse> listRoutes(Long parkId) {
        return dispatchRouteService.listRoutes(parkId).stream()
                .map(this::toRouteResponse)
                .toList();
    }

    @Override
    @Transactional
    public AdminDispatchRouteResponse createRoute(AdminDispatchRouteUpsertRequest request) {
        ensureUniqueRouteCode(request.getParkId(), request.getRouteCode(), null);
        DispatchRouteEntity route = new DispatchRouteEntity();
        applyRouteFields(route, request);
        route.setStatus(resolveStatus(request.getStatus(), "ACTIVE"));
        route.setDeleted(0);
        route.setVersion(0);
        dispatchRouteMapper.insert(route);
        replaceRouteStations(route.getId(), request.getStationIds());
        return toRouteResponse(route);
    }

    @Override
    @Transactional
    public AdminDispatchRouteResponse updateRoute(Long routeId, AdminDispatchRouteUpsertRequest request) {
        DispatchRouteEntity route = requireRoute(routeId);
        ensureUniqueRouteCode(request.getParkId(), request.getRouteCode(), routeId);
        applyRouteFields(route, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            route.setStatus(resolveStatus(request.getStatus(), route.getStatus()));
        }
        dispatchRouteMapper.updateById(route);
        replaceRouteStations(routeId, request.getStationIds());
        return toRouteResponse(route);
    }

    @Override
    @Transactional
    public AdminDispatchRouteResponse toggleRouteStatus(Long routeId) {
        DispatchRouteEntity route = requireRoute(routeId);
        route.setStatus("ACTIVE".equals(route.getStatus()) ? "INACTIVE" : "ACTIVE");
        dispatchRouteMapper.updateById(route);
        return toRouteResponse(route);
    }

    @Override
    public AdminPeakModeResponse getPeakMode(Long parkId) {
        PeakModeStateEntity state = peakModeService.getState(parkId);
        return toPeakModeResponse(state);
    }

    @Override
    public AdminPeakModeResponse updatePeakMode(AdminPeakModeUpsertRequest request) {
        PeakModeStateEntity state = peakModeService.setMode(
                request.getParkId(),
                request.getMode(),
                request.getTemplateCode(),
                request.getScheduleCron(),
                request.getScheduleEndCron());
        return toPeakModeResponse(state);
    }

    @Override
    public AdminHubOverviewResponse getHubOverview(Long parkId) {
        List<StationEntity> hubs = stationMapper.selectList(new LambdaQueryWrapper<StationEntity>()
                .eq(StationEntity::getDeleted, 0)
                .eq(StationEntity::getStatus, "ACTIVE")
                .eq(parkId != null, StationEntity::getParkId, parkId)
                .in(StationEntity::getStationType, HUB_TYPES)
                .orderByAsc(StationEntity::getSortOrder));
        List<AdminHubStationStatusResponse> hubStatuses = hubs.stream()
                .map(station -> {
                    int occupancy = hubCapacityService.countOccupancy(station.getId());
                    Integer limit = station.getCapacityLimit();
                    boolean full = limit != null && limit > 0 && occupancy >= limit;
                    return AdminHubStationStatusResponse.builder()
                            .stationId(station.getId())
                            .stationCode(station.getStationCode())
                            .stationName(station.getStationName())
                            .stationType(station.getStationType())
                            .capacityLimit(limit)
                            .occupancy(occupancy)
                            .full(full)
                            .build();
                })
                .toList();

        List<DispatchTaskEntity> queuedTasks = dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                .eq(DispatchTaskEntity::getDeleted, 0)
                .in(DispatchTaskEntity::getStatus, QUEUED_STATUSES)
                .orderByAsc(DispatchTaskEntity::getCreatedAt));
        Map<Long, OrderEntity> orders = loadOrders(queuedTasks);
        Map<Long, StationEntity> stationById = hubs.stream()
                .collect(Collectors.toMap(StationEntity::getId, Function.identity(), (a, b) -> a));

        List<AdminHubQueuedTaskResponse> hubQueued = new ArrayList<>();
        for (DispatchTaskEntity task : queuedTasks) {
            OrderEntity order = orders.get(task.getOrderId());
            if (order == null) {
                continue;
            }
            Long hubId = resolveHubStationId(order, stationById.keySet());
            if (hubId == null) {
                continue;
            }
            StationEntity hub = stationById.get(hubId);
            if (hub == null) {
                continue;
            }
            hubQueued.add(AdminHubQueuedTaskResponse.builder()
                    .taskId(task.getId())
                    .taskNo(task.getTaskNo())
                    .orderId(order.getId())
                    .status(task.getStatus())
                    .hubStationId(hubId)
                    .hubStationName(hub.getStationName())
                    .suggestion(hubCapacityService.isHubCapacityAvailable(hubId)
                            ? "可尝试自动派车"
                            : "枢纽已满，建议改派至其他缓冲点或等待释放")
                    .build());
        }

        return AdminHubOverviewResponse.builder()
                .hubs(hubStatuses)
                .queuedTasks(hubQueued)
                .build();
    }

    @Override
    public List<AdminAutomationRuleResponse> listAutomationRules(Long parkId) {
        LambdaQueryWrapper<DispatchAutomationRuleEntity> wrapper = new LambdaQueryWrapper<DispatchAutomationRuleEntity>()
                .eq(DispatchAutomationRuleEntity::getDeleted, 0)
                .orderByDesc(DispatchAutomationRuleEntity::getUpdatedAt);
        if (parkId != null) {
            wrapper.eq(DispatchAutomationRuleEntity::getParkId, parkId);
        }
        return automationRuleMapper.selectList(wrapper).stream().map(this::toRuleResponse).toList();
    }

    @Override
    @Transactional
    public AdminAutomationRuleResponse createAutomationRule(AdminAutomationRuleUpsertRequest request, String operator) {
        DispatchAutomationRuleEntity rule = new DispatchAutomationRuleEntity();
        applyRuleFields(rule, request);
        rule.setEnabled(Boolean.FALSE.equals(request.getEnabled()) ? 0 : 1);
        rule.setDeleted(0);
        automationRuleMapper.insert(rule);
        auditRule(rule.getId(), "CREATE", operator, "Created rule " + rule.getRuleName());
        return toRuleResponse(rule);
    }

    @Override
    @Transactional
    public AdminAutomationRuleResponse updateAutomationRule(Long ruleId,
                                                            AdminAutomationRuleUpsertRequest request,
                                                            String operator) {
        DispatchAutomationRuleEntity rule = requireRule(ruleId);
        applyRuleFields(rule, request);
        if (request.getEnabled() != null) {
            rule.setEnabled(Boolean.TRUE.equals(request.getEnabled()) ? 1 : 0);
        }
        automationRuleMapper.updateById(rule);
        auditRule(ruleId, "UPDATE", operator, "Updated rule " + rule.getRuleName());
        return toRuleResponse(rule);
    }

    @Override
    @Transactional
    public void deleteAutomationRule(Long ruleId, String operator) {
        DispatchAutomationRuleEntity rule = requireRule(ruleId);
        rule.setDeleted(1);
        automationRuleMapper.updateById(rule);
        auditRule(ruleId, "DELETE", operator, "Deleted rule " + rule.getRuleName());
    }

    @Override
    @Transactional
    public AdminAutomationRuleResponse toggleAutomationRule(Long ruleId, String operator) {
        DispatchAutomationRuleEntity rule = requireRule(ruleId);
        rule.setEnabled(rule.getEnabled() != null && rule.getEnabled() == 1 ? 0 : 1);
        automationRuleMapper.updateById(rule);
        auditRule(ruleId, "TOGGLE", operator, "Toggled rule " + rule.getRuleName() + " enabled=" + rule.getEnabled());
        return toRuleResponse(rule);
    }

    @Override
    public List<AdminAutomationRuleAuditResponse> listAutomationRuleAudit(Long ruleId) {
        requireRule(ruleId);
        return automationRuleAuditMapper.selectList(new LambdaQueryWrapper<DispatchAutomationRuleAuditEntity>()
                        .eq(DispatchAutomationRuleAuditEntity::getRuleId, ruleId)
                        .orderByDesc(DispatchAutomationRuleAuditEntity::getCreatedAt))
                .stream()
                .map(audit -> AdminAutomationRuleAuditResponse.builder()
                        .id(audit.getId())
                        .ruleId(audit.getRuleId())
                        .action(audit.getAction())
                        .operator(audit.getOperator())
                        .detail(audit.getDetail())
                        .createdAt(audit.getCreatedAt())
                        .build())
                .toList();
    }

    private AdminDispatchRouteResponse toRouteResponse(DispatchRouteEntity route) {
        List<DispatchRouteStationEntity> stops = dispatchRouteStationMapper.selectList(
                new LambdaQueryWrapper<DispatchRouteStationEntity>()
                        .eq(DispatchRouteStationEntity::getRouteId, route.getId())
                        .orderByAsc(DispatchRouteStationEntity::getSequenceNo));
        Map<Long, StationEntity> stationById = stationMapper.selectBatchIds(
                        stops.stream().map(DispatchRouteStationEntity::getStationId).toList())
                .stream()
                .collect(Collectors.toMap(StationEntity::getId, Function.identity(), (a, b) -> a));
        List<AdminRouteStationResponse> stations = stops.stream()
                .map(stop -> {
                    StationEntity station = stationById.get(stop.getStationId());
                    return AdminRouteStationResponse.builder()
                            .stationId(stop.getStationId())
                            .stationCode(station != null ? station.getStationCode() : null)
                            .stationName(station != null ? station.getStationName() : null)
                            .stationType(station != null ? station.getStationType() : null)
                            .sequenceNo(stop.getSequenceNo())
                            .build();
                })
                .toList();
        return AdminDispatchRouteResponse.builder()
                .id(route.getId())
                .parkId(route.getParkId())
                .routeCode(route.getRouteCode())
                .routeName(route.getRouteName())
                .status(route.getStatus())
                .serviceStartTime(route.getServiceStartTime())
                .serviceEndTime(route.getServiceEndTime())
                .requiredVehicleType(route.getRequiredVehicleType())
                .maxConcurrentTasks(route.getMaxConcurrentTasks())
                .activeTaskCount(dispatchRouteService.countActiveTasksOnRoute(route.getId()))
                .stations(stations)
                .remark(route.getRemark())
                .updatedAt(route.getUpdatedAt())
                .build();
    }

    private void replaceRouteStations(Long routeId, List<Long> stationIds) {
        if (stationIds == null || stationIds.size() < 2) {
            throw new BusinessException("ROUTE_STATIONS_INVALID", "线路至少需要 2 个站点");
        }
        dispatchRouteStationMapper.delete(new LambdaQueryWrapper<DispatchRouteStationEntity>()
                .eq(DispatchRouteStationEntity::getRouteId, routeId));
        int seq = 1;
        for (Long stationId : stationIds) {
            DispatchRouteStationEntity stop = new DispatchRouteStationEntity();
            stop.setRouteId(routeId);
            stop.setStationId(stationId);
            stop.setSequenceNo(seq++);
            dispatchRouteStationMapper.insert(stop);
        }
    }

    private void applyRouteFields(DispatchRouteEntity route, AdminDispatchRouteUpsertRequest request) {
        route.setParkId(request.getParkId());
        route.setRouteCode(request.getRouteCode().trim());
        route.setRouteName(request.getRouteName().trim());
        route.setServiceStartTime(request.getServiceStartTime());
        route.setServiceEndTime(request.getServiceEndTime());
        route.setRequiredVehicleType(request.getRequiredVehicleType());
        route.setMaxConcurrentTasks(request.getMaxConcurrentTasks());
        route.setRemark(request.getRemark());
    }

    private AdminPeakModeResponse toPeakModeResponse(PeakModeStateEntity state) {
        return AdminPeakModeResponse.builder()
                .parkId(state.getParkId())
                .mode(state.getMode())
                .templateCode(state.getTemplateCode())
                .scheduleCron(state.getScheduleCron())
                .scheduleEndCron(state.getScheduleEndCron())
                .enabledAt(state.getEnabledAt())
                .updatedAt(state.getUpdatedAt())
                .build();
    }

    private AdminAutomationRuleResponse toRuleResponse(DispatchAutomationRuleEntity rule) {
        return AdminAutomationRuleResponse.builder()
                .id(rule.getId())
                .parkId(rule.getParkId())
                .ruleName(rule.getRuleName())
                .conditionType(rule.getConditionType())
                .conditionValue(rule.getConditionValue())
                .actionType(rule.getActionType())
                .actionParamsJson(rule.getActionParamsJson())
                .enabled(rule.getEnabled() != null && rule.getEnabled() == 1)
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private void applyRuleFields(DispatchAutomationRuleEntity rule, AdminAutomationRuleUpsertRequest request) {
        rule.setParkId(request.getParkId());
        rule.setRuleName(request.getRuleName().trim());
        rule.setConditionType(request.getConditionType());
        rule.setConditionValue(request.getConditionValue());
        rule.setActionType(request.getActionType());
        rule.setActionParamsJson(request.getActionParamsJson());
    }

    private void auditRule(Long ruleId, String action, String operator, String detail) {
        DispatchAutomationRuleAuditEntity audit = new DispatchAutomationRuleAuditEntity();
        audit.setRuleId(ruleId);
        audit.setAction(action);
        audit.setOperator(operator);
        audit.setDetail(detail);
        audit.setCreatedAt(LocalDateTime.now());
        automationRuleAuditMapper.insert(audit);
    }

    private DispatchRouteEntity requireRoute(Long routeId) {
        DispatchRouteEntity route = dispatchRouteMapper.selectOne(new LambdaQueryWrapper<DispatchRouteEntity>()
                .eq(DispatchRouteEntity::getId, routeId)
                .eq(DispatchRouteEntity::getDeleted, 0));
        if (route == null) {
            throw new BusinessException("ROUTE_NOT_FOUND", "线路不存在");
        }
        return route;
    }

    private DispatchAutomationRuleEntity requireRule(Long ruleId) {
        DispatchAutomationRuleEntity rule = automationRuleMapper.selectOne(new LambdaQueryWrapper<DispatchAutomationRuleEntity>()
                .eq(DispatchAutomationRuleEntity::getId, ruleId)
                .eq(DispatchAutomationRuleEntity::getDeleted, 0));
        if (rule == null) {
            throw new BusinessException("RULE_NOT_FOUND", "规则不存在");
        }
        return rule;
    }

    private void ensureUniqueRouteCode(Long parkId, String routeCode, Long exceptId) {
        DispatchRouteEntity existing = dispatchRouteMapper.selectOne(new LambdaQueryWrapper<DispatchRouteEntity>()
                .eq(DispatchRouteEntity::getParkId, parkId)
                .eq(DispatchRouteEntity::getRouteCode, routeCode.trim())
                .eq(DispatchRouteEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !Objects.equals(existing.getId(), exceptId))) {
            throw new BusinessException("ROUTE_CODE_DUPLICATE", "线路编码已存在");
        }
    }

    private String resolveStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        if ("ACTIVE".equals(status) || "INACTIVE".equals(status)) {
            return status;
        }
        throw new BusinessException("ROUTE_STATUS_INVALID", "无效的线路状态");
    }

    private Map<Long, OrderEntity> loadOrders(List<DispatchTaskEntity> tasks) {
        List<Long> orderIds = tasks.stream().map(DispatchTaskEntity::getOrderId).filter(Objects::nonNull).distinct().toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderMapper.selectList(new LambdaQueryWrapper<OrderEntity>()
                        .in(OrderEntity::getId, orderIds)
                        .eq(OrderEntity::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(OrderEntity::getId, Function.identity(), (a, b) -> a));
    }

    private Long resolveHubStationId(OrderEntity order, Set<Long> hubStationIds) {
        if (hubStationIds.contains(order.getDropoffPointId())) {
            return order.getDropoffPointId();
        }
        if (hubStationIds.contains(order.getPickupPointId())) {
            return order.getPickupPointId();
        }
        return null;
    }
}

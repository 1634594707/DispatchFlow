package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.enums.ExceptionSeverity;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.event.DispatchEventType;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchExceptionServiceImpl implements DispatchExceptionService {

    private static final Set<String> ALLOWED_ACTIONS = Set.of(
            "REASSIGN", "MARK_FAILED", "CLOSE", "VEHICLE_OFFLINE", "AUTO_RESOLVED");

    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final DispatchEventPublisher eventPublisher;
    private final DispatchTaskOperateLogService operateLogService;

    public DispatchExceptionServiceImpl(DispatchExceptionRecordMapper exceptionRecordMapper,
                                        DispatchEventPublisher eventPublisher,
                                        DispatchTaskOperateLogService operateLogService) {
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.eventPublisher = eventPublisher;
        this.operateLogService = operateLogService;
    }

    @Override
    @Transactional
    public void recordException(Long taskId, Long orderId, Long vehicleId, String exceptionType, String exceptionMsg) {
        recordException(taskId, orderId, vehicleId, exceptionType, exceptionMsg, ExceptionSeverity.WARN.name());
    }

    @Override
    @Transactional
    public void recordException(Long taskId, Long orderId, Long vehicleId, String exceptionType, String exceptionMsg,
                                String severity) {
        if (ExceptionSeverity.INFO.name().equals(severity)) {
            return;
        }
        if (hasOpenException(taskId, exceptionType)) {
            return;
        }
        DispatchExceptionRecordEntity entity = new DispatchExceptionRecordEntity();
        entity.setTaskId(taskId);
        entity.setOrderId(orderId);
        entity.setVehicleId(vehicleId);
        entity.setExceptionType(exceptionType);
        entity.setExceptionStatus("OPEN");
        entity.setExceptionMsg(exceptionMsg);
        entity.setSeverity(severity);
        entity.setOccurTime(LocalDateTime.now());
        exceptionRecordMapper.insert(entity);
        eventPublisher.publish(DispatchEventType.EXCEPTION_OPEN, String.valueOf(entity.getId()), buildPayload(entity));
    }

    @Override
    @Transactional
    public void resolveException(Long exceptionId, DispatchExceptionResolveRequest request) {
        validateAction(request.getAction());
        DispatchExceptionRecordEntity entity = exceptionRecordMapper.selectById(exceptionId);
        if (entity == null) {
            throw new BusinessException("DISPATCH_EXCEPTION_NOT_FOUND", "Dispatch exception not found");
        }
        if ("RESOLVED".equals(entity.getExceptionStatus())) {
            throw new BusinessException("DISPATCH_EXCEPTION_ALREADY_RESOLVED", "Dispatch exception already resolved");
        }
        markResolved(entity, request.getResolverId(), request.getAction(), request.getRemark());
    }

    @Override
    @Transactional
    public void resolveOpenExceptionsForTask(Long taskId, String resolverId, String remark) {
        if (taskId == null) {
            return;
        }
        List<DispatchExceptionRecordEntity> openExceptions = listOpenExceptionsByTaskId(taskId);
        for (DispatchExceptionRecordEntity entity : openExceptions) {
            markResolved(entity, resolverId, "AUTO_RESOLVED", remark);
        }
    }

    @Override
    public List<DispatchExceptionRecordEntity> listOpenExceptions() {
        return exceptionRecordMapper.selectList(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                .eq(DispatchExceptionRecordEntity::getExceptionStatus, "OPEN")
                .orderByDesc(DispatchExceptionRecordEntity::getOccurTime));
    }

    @Override
    public List<DispatchExceptionRecordEntity> listOpenExceptionsByTaskId(Long taskId) {
        return exceptionRecordMapper.selectList(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                .eq(DispatchExceptionRecordEntity::getTaskId, taskId)
                .eq(DispatchExceptionRecordEntity::getExceptionStatus, "OPEN")
                .orderByDesc(DispatchExceptionRecordEntity::getOccurTime));
    }

    private void markResolved(DispatchExceptionRecordEntity entity, String resolverId, String action, String remark) {
        entity.setExceptionStatus("RESOLVED");
        entity.setResolvedTime(LocalDateTime.now());
        entity.setResolverId(resolverId);
        entity.setResolveAction(action);
        entity.setResolveRemark(action + ": " + (remark == null ? "" : remark));
        exceptionRecordMapper.updateById(entity);
        if (entity.getTaskId() != null) {
            operateLogService.record(entity.getTaskId(), "EXCEPTION_RESOLVE", action, "RESOLVED",
                    "DISPATCHER", resolverId, resolverId, remark);
        }
        eventPublisher.publish(DispatchEventType.EXCEPTION_RESOLVED, String.valueOf(entity.getId()), buildPayload(entity));
    }

    private void validateAction(String action) {
        if (action == null || !ALLOWED_ACTIONS.contains(action)) {
            throw new BusinessException("DISPATCH_EXCEPTION_ACTION_INVALID", "Unsupported exception resolve action");
        }
    }

    private boolean hasOpenException(Long taskId, String exceptionType) {
        if (taskId == null || exceptionType == null) {
            return false;
        }
        Long count = exceptionRecordMapper.selectCount(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                .eq(DispatchExceptionRecordEntity::getTaskId, taskId)
                .eq(DispatchExceptionRecordEntity::getExceptionType, exceptionType)
                .eq(DispatchExceptionRecordEntity::getExceptionStatus, "OPEN"));
        return count != null && count > 0;
    }

    private Map<String, Object> buildPayload(DispatchExceptionRecordEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exceptionId", entity.getId());
        payload.put("taskId", entity.getTaskId());
        payload.put("orderId", entity.getOrderId());
        payload.put("vehicleId", entity.getVehicleId());
        payload.put("exceptionType", entity.getExceptionType());
        payload.put("exceptionStatus", entity.getExceptionStatus());
        payload.put("exceptionMsg", entity.getExceptionMsg());
        payload.put("severity", entity.getSeverity());
        payload.put("resolveAction", entity.getResolveAction());
        payload.put("occurTime", entity.getOccurTime());
        payload.put("resolvedTime", entity.getResolvedTime());
        payload.put("resolverId", entity.getResolverId());
        payload.put("resolveRemark", entity.getResolveRemark());
        return payload;
    }
}

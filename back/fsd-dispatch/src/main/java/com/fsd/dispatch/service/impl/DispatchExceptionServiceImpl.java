package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.event.DispatchEventType;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchExceptionServiceImpl implements DispatchExceptionService {

    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final DispatchEventPublisher eventPublisher;

    public DispatchExceptionServiceImpl(DispatchExceptionRecordMapper exceptionRecordMapper,
                                        DispatchEventPublisher eventPublisher) {
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void recordException(Long taskId, Long orderId, Long vehicleId, String exceptionType, String exceptionMsg) {
        DispatchExceptionRecordEntity entity = new DispatchExceptionRecordEntity();
        entity.setTaskId(taskId);
        entity.setOrderId(orderId);
        entity.setVehicleId(vehicleId);
        entity.setExceptionType(exceptionType);
        entity.setExceptionStatus("OPEN");
        entity.setExceptionMsg(exceptionMsg);
        entity.setOccurTime(LocalDateTime.now());
        exceptionRecordMapper.insert(entity);
        eventPublisher.publish(DispatchEventType.EXCEPTION_OPEN, String.valueOf(entity.getId()), buildPayload(entity));
    }

    @Override
    @Transactional
    public void resolveException(Long exceptionId, DispatchExceptionResolveRequest request) {
        DispatchExceptionRecordEntity entity = exceptionRecordMapper.selectById(exceptionId);
        if (entity == null) {
            throw new BusinessException("DISPATCH_EXCEPTION_NOT_FOUND", "Dispatch exception not found");
        }
        if ("RESOLVED".equals(entity.getExceptionStatus())) {
            throw new BusinessException("DISPATCH_EXCEPTION_ALREADY_RESOLVED", "Dispatch exception already resolved");
        }
        entity.setExceptionStatus("RESOLVED");
        entity.setResolvedTime(LocalDateTime.now());
        entity.setResolverId(request.getResolverId());
        entity.setResolveRemark(request.getAction() + ": " + request.getRemark());
        exceptionRecordMapper.updateById(entity);
        eventPublisher.publish(DispatchEventType.EXCEPTION_RESOLVED, String.valueOf(entity.getId()), buildPayload(entity));
    }

    @Override
    public List<DispatchExceptionRecordEntity> listOpenExceptions() {
        return exceptionRecordMapper.selectList(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                .eq(DispatchExceptionRecordEntity::getExceptionStatus, "OPEN")
                .orderByDesc(DispatchExceptionRecordEntity::getOccurTime));
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
        payload.put("occurTime", entity.getOccurTime());
        payload.put("resolvedTime", entity.getResolvedTime());
        payload.put("resolverId", entity.getResolverId());
        payload.put("resolveRemark", entity.getResolveRemark());
        return payload;
    }
}

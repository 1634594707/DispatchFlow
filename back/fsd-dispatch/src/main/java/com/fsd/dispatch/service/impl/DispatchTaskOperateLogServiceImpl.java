package com.fsd.dispatch.service.impl;

import com.fsd.dispatch.entity.DispatchTaskOperateLogEntity;
import com.fsd.dispatch.mapper.DispatchTaskOperateLogMapper;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchTaskOperateLogServiceImpl implements DispatchTaskOperateLogService {

    private final DispatchTaskOperateLogMapper operateLogMapper;

    public DispatchTaskOperateLogServiceImpl(DispatchTaskOperateLogMapper operateLogMapper) {
        this.operateLogMapper = operateLogMapper;
    }

    @Override
    @Transactional
    public void record(Long taskId, String operateType, String beforeStatus, String afterStatus,
                       String operatorType, String operatorId, String operatorName, String remark) {
        DispatchTaskOperateLogEntity entity = new DispatchTaskOperateLogEntity();
        entity.setTaskId(taskId);
        entity.setOperateType(operateType);
        entity.setBeforeStatus(beforeStatus);
        entity.setAfterStatus(afterStatus);
        entity.setOperatorType(operatorType);
        entity.setOperatorId(operatorId);
        entity.setOperatorName(operatorName);
        entity.setOperateRemark(remark);
        entity.setCreatedAt(LocalDateTime.now());
        operateLogMapper.insert(entity);
    }
}

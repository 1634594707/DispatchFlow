package com.fsd.dispatch.service;

public interface DispatchTaskOperateLogService {

    void record(Long taskId, String operateType, String beforeStatus, String afterStatus,
                String operatorType, String operatorId, String operatorName, String remark);

    java.util.List<com.fsd.dispatch.entity.DispatchTaskOperateLogEntity> listByTaskId(Long taskId);

    java.util.List<com.fsd.dispatch.entity.DispatchTaskOperateLogEntity> listByTaskIds(java.util.Collection<Long> taskIds);
}

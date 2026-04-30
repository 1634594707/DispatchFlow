package com.fsd.dispatch.service;

public interface DispatchTaskOperateLogService {

    void record(Long taskId, String operateType, String beforeStatus, String afterStatus,
                String operatorType, String operatorId, String operatorName, String remark);
}

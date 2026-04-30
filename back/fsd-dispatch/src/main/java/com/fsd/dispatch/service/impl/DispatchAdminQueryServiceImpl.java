package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.vo.DispatchSummaryResponse;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DispatchAdminQueryServiceImpl implements DispatchAdminQueryService {

    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchExceptionRecordMapper exceptionRecordMapper;
    private final DispatchTaskService dispatchTaskService;

    public DispatchAdminQueryServiceImpl(DispatchTaskMapper dispatchTaskMapper,
                                         DispatchExceptionRecordMapper exceptionRecordMapper,
                                         DispatchTaskService dispatchTaskService) {
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.exceptionRecordMapper = exceptionRecordMapper;
        this.dispatchTaskService = dispatchTaskService;
    }

    @Override
    public List<DispatchTaskListItemResponse> listTasks() {
        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                        .eq(DispatchTaskEntity::getDeleted, 0)
                        .orderByDesc(DispatchTaskEntity::getCreatedAt))
                .stream()
                .map(task -> DispatchTaskListItemResponse.builder()
                        .taskId(task.getId())
                        .taskNo(task.getTaskNo())
                        .orderId(task.getOrderId())
                        .vehicleId(task.getVehicleId())
                        .status(task.getStatus())
                        .failReasonCode(task.getFailReasonCode())
                        .failReasonMsg(task.getFailReasonMsg())
                        .createdAt(task.getCreatedAt())
                        .updatedAt(task.getUpdatedAt())
                        .build())
                .toList();
    }

    @Override
    public DispatchTaskDetailResponse getTaskDetail(Long taskId) {
        return dispatchTaskService.getTaskDetail(taskId);
    }

    @Override
    public List<DispatchExceptionRecordEntity> listExceptions() {
        return exceptionRecordMapper.selectList(new LambdaQueryWrapper<DispatchExceptionRecordEntity>()
                .orderByDesc(DispatchExceptionRecordEntity::getOccurTime));
    }

    @Override
    public DispatchSummaryResponse getSummary() {
        return dispatchTaskService.getSummary();
    }
}

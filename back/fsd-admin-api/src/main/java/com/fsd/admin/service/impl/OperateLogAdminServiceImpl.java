package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.admin.dto.AdminOperateLogQueryRequest;
import com.fsd.admin.service.OperateLogAdminService;
import com.fsd.admin.vo.AdminOperateLogResponse;
import com.fsd.common.model.PageResponse;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.DispatchTaskOperateLogEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.DispatchTaskOperateLogMapper;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OperateLogAdminServiceImpl implements OperateLogAdminService {

    private final DispatchTaskOperateLogMapper operateLogMapper;
    private final DispatchTaskOperateLogService operateLogService;
    private final DispatchTaskMapper dispatchTaskMapper;

    public OperateLogAdminServiceImpl(DispatchTaskOperateLogMapper operateLogMapper,
                                      DispatchTaskOperateLogService operateLogService,
                                      DispatchTaskMapper dispatchTaskMapper) {
        this.operateLogMapper = operateLogMapper;
        this.operateLogService = operateLogService;
        this.dispatchTaskMapper = dispatchTaskMapper;
    }

    @Override
    public PageResponse<AdminOperateLogResponse> queryLogs(AdminOperateLogQueryRequest request) {
        List<AdminOperateLogResponse> all = filterLogs(request);
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 20 : request.getPageSize();
        int from = (pageNo - 1) * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<AdminOperateLogResponse> page = from >= all.size() ? List.of() : all.subList(from, to);
        return PageResponse.<AdminOperateLogResponse>builder()
                .total(all.size())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .records(page)
                .build();
    }

    @Override
    public List<AdminOperateLogResponse> listByTaskId(Long taskId) {
        return operateLogService.listByTaskId(taskId).stream()
                .map(log -> toResponse(log, taskNoMap(List.of(taskId)).get(taskId)))
                .toList();
    }

    @Override
    public List<AdminOperateLogResponse> listByVehicleId(Long vehicleId) {
        List<Long> taskIds = dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                        .eq(DispatchTaskEntity::getVehicleId, vehicleId)
                        .eq(DispatchTaskEntity::getDeleted, 0))
                .stream()
                .map(DispatchTaskEntity::getId)
                .toList();
        if (taskIds.isEmpty()) {
            return List.of();
        }
        Map<Long, String> taskNoById = taskNoMap(taskIds);
        return operateLogService.listByTaskIds(taskIds).stream()
                .map(log -> toResponse(log, taskNoById.get(log.getTaskId())))
                .toList();
    }

    @Override
    public String exportCsv(AdminOperateLogQueryRequest request) {
        List<AdminOperateLogResponse> logs = filterLogs(request);
        StringBuilder sb = new StringBuilder();
        sb.append("id,taskId,taskNo,operateType,beforeStatus,afterStatus,operatorType,operatorId,operatorName,operateRemark,createdAt\n");
        for (AdminOperateLogResponse log : logs) {
            sb.append(csv(log.getId()))
                    .append(',').append(csv(log.getTaskId()))
                    .append(',').append(csv(log.getTaskNo()))
                    .append(',').append(csv(log.getOperateType()))
                    .append(',').append(csv(log.getBeforeStatus()))
                    .append(',').append(csv(log.getAfterStatus()))
                    .append(',').append(csv(log.getOperatorType()))
                    .append(',').append(csv(log.getOperatorId()))
                    .append(',').append(csv(log.getOperatorName()))
                    .append(',').append(csv(log.getOperateRemark()))
                    .append(',').append(csv(log.getCreatedAt()))
                    .append('\n');
        }
        return sb.toString();
    }

    private List<AdminOperateLogResponse> filterLogs(AdminOperateLogQueryRequest request) {
        LambdaQueryWrapper<DispatchTaskOperateLogEntity> wrapper = new LambdaQueryWrapper<>();
        if (request.getTaskId() != null) {
            wrapper.eq(DispatchTaskOperateLogEntity::getTaskId, request.getTaskId());
        }
        if (request.getOperateType() != null && !request.getOperateType().isBlank()) {
            wrapper.eq(DispatchTaskOperateLogEntity::getOperateType, request.getOperateType());
        }
        if (request.getOperatorName() != null && !request.getOperatorName().isBlank()) {
            wrapper.like(DispatchTaskOperateLogEntity::getOperatorName, request.getOperatorName().trim());
        }
        if (request.getStartTime() != null) {
            wrapper.ge(DispatchTaskOperateLogEntity::getCreatedAt, request.getStartTime());
        }
        if (request.getEndTime() != null) {
            wrapper.le(DispatchTaskOperateLogEntity::getCreatedAt, request.getEndTime());
        }
        if (request.getVehicleId() != null) {
            List<Long> taskIds = dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                            .eq(DispatchTaskEntity::getVehicleId, request.getVehicleId())
                            .eq(DispatchTaskEntity::getDeleted, 0))
                    .stream()
                    .map(DispatchTaskEntity::getId)
                    .toList();
            if (taskIds.isEmpty()) {
                return List.of();
            }
            wrapper.in(DispatchTaskOperateLogEntity::getTaskId, taskIds);
        }
        wrapper.orderByDesc(DispatchTaskOperateLogEntity::getCreatedAt);
        List<DispatchTaskOperateLogEntity> entities = operateLogMapper.selectList(wrapper);
        List<Long> taskIds = entities.stream().map(DispatchTaskOperateLogEntity::getTaskId).distinct().toList();
        Map<Long, String> taskNoById = taskNoMap(taskIds);
        return entities.stream()
                .map(entity -> toResponse(entity, taskNoById.get(entity.getTaskId())))
                .sorted(Comparator.comparing(AdminOperateLogResponse::getCreatedAt).reversed())
                .toList();
    }

    private Map<Long, String> taskNoMap(List<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return dispatchTaskMapper.selectList(new LambdaQueryWrapper<DispatchTaskEntity>()
                        .in(DispatchTaskEntity::getId, taskIds))
                .stream()
                .collect(Collectors.toMap(DispatchTaskEntity::getId, DispatchTaskEntity::getTaskNo));
    }

    private AdminOperateLogResponse toResponse(DispatchTaskOperateLogEntity entity, String taskNo) {
        Long vehicleId = null;
        if (entity.getTaskId() != null) {
            DispatchTaskEntity task = dispatchTaskMapper.selectById(entity.getTaskId());
            if (task != null) {
                vehicleId = task.getVehicleId();
            }
        }
        return AdminOperateLogResponse.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .taskNo(taskNo)
                .vehicleId(vehicleId)
                .operateType(entity.getOperateType())
                .beforeStatus(entity.getBeforeStatus())
                .afterStatus(entity.getAfterStatus())
                .operatorType(entity.getOperatorType())
                .operatorId(entity.getOperatorId())
                .operatorName(entity.getOperatorName())
                .operateRemark(entity.getOperateRemark())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = Objects.toString(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}

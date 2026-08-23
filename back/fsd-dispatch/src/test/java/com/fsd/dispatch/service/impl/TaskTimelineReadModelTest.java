package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.DispatchTaskOperateLogEntity;
import com.fsd.dispatch.fleet.policy.FleetChargePolicy;
import com.fsd.dispatch.fleet.service.FleetRuntimeService;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.DispatchTaskOperateLogMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchRouteService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.TaskTimelineResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 路线图 3.2：统一任务时间线读取模型 —— 串联订单创建、派车、回报、异常、重试、改派、取消和完成事件。
 */
class TaskTimelineReadModelTest {

    private DispatchTaskMapper dispatchTaskMapper;
    private DispatchExceptionRecordMapper exceptionRecordMapper;
    private DispatchTaskOperateLogMapper operateLogMapper;
    private OrderMapper orderMapper;
    private DispatchAdminQueryServiceImpl queryService;

    @BeforeEach
    void setUp() {
        dispatchTaskMapper = mock(DispatchTaskMapper.class);
        exceptionRecordMapper = mock(DispatchExceptionRecordMapper.class);
        operateLogMapper = mock(DispatchTaskOperateLogMapper.class);
        orderMapper = mock(OrderMapper.class);
        queryService = new DispatchAdminQueryServiceImpl(
                dispatchTaskMapper,
                exceptionRecordMapper,
                operateLogMapper,
                mock(DispatchTaskService.class),
                mock(DispatchExceptionService.class),
                mock(ParkPilotService.class),
                mock(FleetRuntimeService.class),
                mock(FleetChargePolicy.class),
                orderMapper,
                mock(DispatchRouteService.class));
    }

    @Test
    void timelineShouldMergeOrderCreationLogsAndExceptionsChronologically() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 24, 10, 0, 0);

        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(11L);
        task.setTaskNo("TSK-011");
        task.setOrderId(22L);
        task.setStatus("ASSIGNED");
        when(dispatchTaskMapper.selectById(11L)).thenReturn(task);

        OrderEntity order = new OrderEntity();
        order.setId(22L);
        order.setSourceType("PARK");
        order.setStatus("DISPATCHED");
        order.setCreatedAt(base);
        when(orderMapper.selectById(22L)).thenReturn(order);

        DispatchTaskOperateLogEntity createLog = log("CREATE_TASK", null, "PENDING", "SYSTEM", "system", base.plusMinutes(1));
        DispatchTaskOperateLogEntity assignLog = log("AUTO_ASSIGN", "ASSIGNING", "ASSIGNED", "SYSTEM", "system", base.plusMinutes(2), "Auto assign success");
        DispatchTaskOperateLogEntity reportLog = log("START_EXECUTE", "ASSIGNED", "EXECUTING", "VEHICLE", "V-001", base.plusMinutes(5), "");
        when(operateLogMapper.selectList(any())).thenReturn(List.of(createLog, assignLog, reportLog));

        DispatchExceptionRecordEntity exception = new DispatchExceptionRecordEntity();
        exception.setTaskId(11L);
        exception.setOccurTime(base.plusMinutes(3));
        exception.setResolvedTime(base.plusMinutes(4));
        exception.setExceptionType("ROUTE_UNREACHABLE");
        exception.setExceptionStatus("OPEN");
        exception.setExceptionMsg("路线不可达");
        exception.setSeverity("HIGH");
        exception.setResolveAction("人工改派");
        when(exceptionRecordMapper.selectList(any())).thenReturn(List.of(exception));

        TaskTimelineResponse timeline = queryService.getTaskTimeline(11L);

        assertEquals("TSK-011", timeline.getTaskNo());
        assertEquals(6, timeline.getEntries().size());
        // 严格按时间升序：订单创建 → 创建任务 → 自动派车 → 异常上报 → 异常解除 → 车辆回报开始执行
        List<String> types = timeline.getEntries().stream().map(TaskTimelineResponse.Entry::getEventType).toList();
        assertEquals(List.of(
                "CREATE_ORDER", "CREATE_TASK", "AUTO_ASSIGN",
                "EXCEPTION_RAISED", "EXCEPTION_RESOLVED",
                "START_EXECUTE"), types);

        TaskTimelineResponse.Entry first = timeline.getEntries().get(0);
        assertEquals("MOBILE", first.getSource());
        assertNotNull(first.getTime());

        TaskTimelineResponse.Entry raised = timeline.getEntries().get(3);
        assertEquals(Boolean.TRUE, raised.getException());
        assertEquals("HIGH", raised.getSeverity());
        assertEquals("路线不可达", raised.getFailReason());

        TaskTimelineResponse.Entry vehicleReport = timeline.getEntries().get(5);
        assertEquals("VEHICLE", vehicleReport.getSource());
        assertEquals("ASSIGNED", vehicleReport.getBeforeStatus());
        assertEquals("EXECUTING", vehicleReport.getAfterStatus());
    }

    @Test
    void failureLogShouldCarryFailReasonAndHighlight() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 24, 12, 0, 0);
        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(31L);
        task.setTaskNo("TSK-031");
        task.setStatus("FAILED");
        task.setFailReasonMsg("车辆低电量");
        when(dispatchTaskMapper.selectById(31L)).thenReturn(task);

        DispatchTaskOperateLogEntity failLog = log("FINISH_FAILED", "EXECUTING", "FAILED", "VEHICLE", "V-002", base.plusMinutes(2), "电量不足终止配送");
        when(operateLogMapper.selectList(any())).thenReturn(List.of(failLog));
        when(exceptionRecordMapper.selectList(any())).thenReturn(List.of());

        TaskTimelineResponse timeline = queryService.getTaskTimeline(31L);

        assertEquals(1, timeline.getEntries().size());
        TaskTimelineResponse.Entry entry = timeline.getEntries().get(0);
        assertEquals(Boolean.TRUE, entry.getException());
        assertEquals("电量不足终止配送", entry.getFailReason());
    }

    @Test
    void missingTaskShouldBeRejected() {
        when(dispatchTaskMapper.selectById(99L)).thenReturn(null);
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> queryService.getTaskTimeline(99L));
        assertEquals("DISPATCH_TASK_NOT_FOUND", ex.getCode());
    }

    private DispatchTaskOperateLogEntity log(String type, String before, String after,
                                             String operatorType, String operatorName, LocalDateTime time) {
        return log(type, before, after, operatorType, operatorName, time, null);
    }

    private DispatchTaskOperateLogEntity log(String type, String before, String after,
                                             String operatorType, String operatorName, LocalDateTime time, String remark) {
        DispatchTaskOperateLogEntity entity = new DispatchTaskOperateLogEntity();
        entity.setTaskId(11L);
        entity.setOperateType(type);
        entity.setBeforeStatus(before);
        entity.setAfterStatus(after);
        entity.setOperatorType(operatorType);
        entity.setOperatorName(operatorName);
        entity.setOperateRemark(remark);
        entity.setCreatedAt(time);
        return entity;
    }
}

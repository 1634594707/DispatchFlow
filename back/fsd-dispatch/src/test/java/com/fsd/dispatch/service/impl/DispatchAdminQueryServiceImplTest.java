package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.stubbing.Answer;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.dispatch.dto.DispatchTaskQueryRequest;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.order.entity.OrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchAdminQueryServiceImplTest {

    @Mock
    private DispatchTaskMapper dispatchTaskMapper;
    @Mock
    private DispatchExceptionRecordMapper exceptionRecordMapper;
    @Mock
    private DispatchTaskService dispatchTaskService;
    @Mock
    private DispatchExceptionService dispatchExceptionService;
    @Mock
    private com.fsd.dispatch.service.ParkPilotService parkPilotService;
    @Mock
    private com.fsd.dispatch.fleet.service.FleetRuntimeService fleetRuntimeService;
    @Mock
    private com.fsd.dispatch.fleet.policy.FleetChargePolicy fleetChargePolicy;
    @Mock
    private com.fsd.order.mapper.OrderMapper orderMapper;
    @Mock
    private com.fsd.dispatch.service.DispatchRouteService dispatchRouteService;

    @InjectMocks
    private DispatchAdminQueryServiceImpl dispatchAdminQueryService;

    @Test
    void listTasksShouldAttachOpenExceptions() {
        DispatchTaskEntity task = buildTask(10L, "TSK-10", DispatchTaskStatus.MANUAL_PENDING.name());
        DispatchExceptionRecordEntity openException = buildOpenException(1L, 10L, "NO_VEHICLE");
        when(dispatchTaskMapper.selectPage(any(), any())).thenAnswer(fillPage(List.of(task), 1));
        when(dispatchExceptionService.listOpenExceptions()).thenReturn(List.of(openException));
        when(orderMapper.selectList(any())).thenReturn(List.of(buildOrder(110L, 1L)));

        var tasks = dispatchAdminQueryService.listTasks();

        assertEquals(1, tasks.size());
        assertEquals(1, tasks.getFirst().getOpenExceptionCount());
        assertNotNull(tasks.getFirst().getPrimaryOpenException());
        assertEquals("NO_VEHICLE", tasks.getFirst().getPrimaryOpenException().getExceptionType());
    }

    @Test
    void getTaskDetailShouldAttachOpenExceptions() {
        DispatchExceptionRecordEntity openException = buildOpenException(2L, 10L, "LOW_SOC");
        when(dispatchTaskService.getTaskDetail(10L)).thenReturn(
                DispatchTaskDetailResponse.builder().taskId(10L).status(DispatchTaskStatus.MANUAL_PENDING.name()).build()
        );
        when(dispatchExceptionService.listOpenExceptionsByTaskId(10L)).thenReturn(List.of(openException));

        var detail = dispatchAdminQueryService.getTaskDetail(10L);

        assertEquals(1, detail.getOpenExceptionCount());
        assertEquals("LOW_SOC", detail.getOpenExceptions().getFirst().getExceptionType());
    }

    @Test
    void listExceptionsShouldAttachTaskSummary() {
        DispatchExceptionRecordEntity openException = buildOpenException(3L, 11L, "NO_VEHICLE");
        DispatchTaskEntity task = buildTask(11L, "TSK-11", DispatchTaskStatus.MANUAL_PENDING.name());

        when(exceptionRecordMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(openException));
        when(dispatchTaskMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(task));

        var exceptions = dispatchAdminQueryService.listExceptions();

        assertEquals("TSK-11", exceptions.getFirst().getTaskNo());
        assertEquals(DispatchTaskStatus.MANUAL_PENDING.name(), exceptions.getFirst().getTaskStatus());
    }

    @Test
    void getInterventionQueueShouldReturnManualPendingAndOpenExceptions() {
        DispatchTaskEntity manualTask = buildTask(20L, "TSK-20", DispatchTaskStatus.MANUAL_PENDING.name());
        DispatchExceptionRecordEntity openException = buildOpenException(4L, 20L, "NO_VEHICLE");

        when(dispatchTaskMapper.selectCount(any())).thenReturn(0L, 1L);
        when(dispatchTaskMapper.selectPage(any(), any()))
                .thenAnswer(fillPage(List.of(), 0))
                .thenAnswer(fillPage(List.of(manualTask), 1));
        when(dispatchExceptionService.listOpenExceptions()).thenReturn(List.of(openException));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(dispatchTaskMapper.selectOne(any())).thenReturn(manualTask);

        var queue = dispatchAdminQueryService.getInterventionQueue();

        assertEquals(1, queue.getManualPendingCount());
        assertEquals(1, queue.getOpenExceptionCount());
        assertEquals(1, queue.getManualPendingTasks().getFirst().getOpenExceptionCount());
        assertEquals("TSK-20", queue.getOpenExceptions().getFirst().getTaskNo());
    }

    @Test
    void queryTasksShouldUseDatabasePagination() {
        DispatchTaskEntity task = buildTask(30L, "TSK-30", DispatchTaskStatus.PENDING.name());
        when(dispatchTaskMapper.selectPage(any(), any())).thenAnswer(fillPage(List.of(task), 1));
        when(dispatchExceptionService.listOpenExceptions()).thenReturn(List.of());
        when(orderMapper.selectList(any())).thenReturn(List.of(buildOrder(130L, 1L)));

        DispatchTaskQueryRequest request = new DispatchTaskQueryRequest();
        request.setPoolStatus("PENDING");
        request.setPageNo(1);
        request.setPageSize(20);

        var result = dispatchAdminQueryService.queryTasks(request);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getRecords().size());
        assertEquals("TSK-30", result.getRecords().getFirst().getTaskNo());
    }

    @SuppressWarnings("unchecked")
    private static Answer<Page<DispatchTaskEntity>> fillPage(List<DispatchTaskEntity> records, long total) {
        return invocation -> {
            Page<DispatchTaskEntity> page = invocation.getArgument(0);
            page.setRecords(records);
            page.setTotal(total);
            return page;
        };
    }

    private DispatchTaskEntity buildTask(Long taskId, String taskNo, String status) {
        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(taskId);
        task.setTaskNo(taskNo);
        task.setOrderId(taskId + 100);
        task.setStatus(status);
        task.setDeleted(0);
        return task;
    }

    private OrderEntity buildOrder(Long orderId, Long parkId) {
        OrderEntity order = new OrderEntity();
        order.setId(orderId);
        order.setParkId(parkId);
        order.setPriority("P2");
        order.setDeleted(0);
        return order;
    }

    private DispatchExceptionRecordEntity buildOpenException(Long exceptionId, Long taskId, String type) {
        DispatchExceptionRecordEntity exception = new DispatchExceptionRecordEntity();
        exception.setId(exceptionId);
        exception.setTaskId(taskId);
        exception.setExceptionType(type);
        exception.setExceptionStatus("OPEN");
        exception.setExceptionMsg("No vehicle available");
        exception.setSeverity("WARN");
        exception.setOccurTime(LocalDateTime.now());
        return exception;
    }
}

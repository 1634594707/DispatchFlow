package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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

    @InjectMocks
    private DispatchAdminQueryServiceImpl dispatchAdminQueryService;

    @Test
    void listTasksShouldAttachOpenExceptions() {
        DispatchTaskEntity task = buildTask(10L, "TSK-10", DispatchTaskStatus.MANUAL_PENDING.name());
        DispatchExceptionRecordEntity openException = buildOpenException(1L, 10L, "NO_VEHICLE");

        when(dispatchTaskMapper.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(task));
        when(dispatchExceptionService.listOpenExceptions()).thenReturn(List.of(openException));

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
        DispatchTaskListItemResponse manualPending = DispatchTaskListItemResponse.builder()
                .taskId(20L)
                .taskNo("TSK-20")
                .status(DispatchTaskStatus.MANUAL_PENDING.name())
                .build();
        DispatchExceptionRecordEntity openException = buildOpenException(4L, 20L, "NO_VEHICLE");

        when(dispatchTaskService.listManualPendingTasks()).thenReturn(List.of(manualPending));
        when(dispatchTaskService.listPendingTasks()).thenReturn(List.of());
        when(dispatchExceptionService.listOpenExceptions()).thenReturn(List.of(openException));
        when(dispatchTaskMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(
                buildTask(20L, "TSK-20", DispatchTaskStatus.MANUAL_PENDING.name())
        );

        var queue = dispatchAdminQueryService.getInterventionQueue();

        assertEquals(1, queue.getManualPendingCount());
        assertEquals(1, queue.getOpenExceptionCount());
        assertEquals(1, queue.getManualPendingTasks().getFirst().getOpenExceptionCount());
        assertEquals("TSK-20", queue.getOpenExceptions().getFirst().getTaskNo());
    }

    private DispatchTaskEntity buildTask(Long taskId, String taskNo, String status) {
        DispatchTaskEntity task = new DispatchTaskEntity();
        task.setId(taskId);
        task.setTaskNo(taskNo);
        task.setStatus(status);
        task.setDeleted(0);
        return task;
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

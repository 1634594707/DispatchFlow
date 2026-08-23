package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 路线图 2.2：任务状态迁移唯一契约。
 *
 * <p>状态表来源：{@link DispatchTaskStatus}；本测试锁定「操作 × 允许前置状态」矩阵，
 * 任何调整必须同时更新枚举注释与本测试。</p>
 */
class DispatchTaskStateContractTest {

    private final DispatchTaskMapper mapper = mock(DispatchTaskMapper.class);
    private final DispatchTaskStateServiceImpl stateService = new DispatchTaskStateServiceImpl(mapper);

    private static final Map<DispatchTaskStatus, Set<DispatchTaskStatus>> EXPECTED_TABLE = Map.of(
            DispatchTaskStatus.PENDING, Set.of(
                    DispatchTaskStatus.ASSIGNING, DispatchTaskStatus.MANUAL_PENDING, DispatchTaskStatus.CANCELLED),
            DispatchTaskStatus.MANUAL_PENDING, Set.of(
                    DispatchTaskStatus.ASSIGNING, DispatchTaskStatus.MANUAL_PENDING, DispatchTaskStatus.CANCELLED),
            DispatchTaskStatus.ASSIGNING, Set.of(
                    DispatchTaskStatus.ASSIGNED, DispatchTaskStatus.MANUAL_PENDING,
                    DispatchTaskStatus.PENDING, DispatchTaskStatus.CANCELLED),
            // ASSIGNED -> PENDING 人工接管退回；ASSIGNED -> ASSIGNED 改派换车自环
            DispatchTaskStatus.ASSIGNED, Set.of(
                    DispatchTaskStatus.EXECUTING, DispatchTaskStatus.SUCCESS,
                    DispatchTaskStatus.CANCELLED, DispatchTaskStatus.PENDING, DispatchTaskStatus.ASSIGNED),
            DispatchTaskStatus.EXECUTING, Set.of(
                    DispatchTaskStatus.SUCCESS, DispatchTaskStatus.FAILED, DispatchTaskStatus.CANCELLED));

    @Test
    void transitionTableShouldMatchContract() {
        for (DispatchTaskStatus status : DispatchTaskStatus.values()) {
            Set<DispatchTaskStatus> expected = EXPECTED_TABLE.getOrDefault(status, Set.of());
            assertEquals(expected, status.allowedNextStatuses(),
                    "状态 " + status + " 的允许目标集合与契约不一致");
        }
        // 终态不允许再流转
        assertFalse(DispatchTaskStatus.SUCCESS.canTransitionTo(DispatchTaskStatus.PENDING));
        assertFalse(DispatchTaskStatus.FAILED.canTransitionTo(DispatchTaskStatus.ASSIGNING));
        assertFalse(DispatchTaskStatus.CANCELLED.canTransitionTo(DispatchTaskStatus.PENDING));
    }

    @Test
    void autoAssignShouldRequirePendingManualPendingOrStuckAssigning() {
        assertOperationAllowed("AUTO_ASSIGN", DispatchTaskStatus.PENDING);
        assertOperationAllowed("AUTO_ASSIGN", DispatchTaskStatus.MANUAL_PENDING);
        assertOperationAllowed("AUTO_ASSIGN", DispatchTaskStatus.ASSIGNING);
        assertOperationDenied("AUTO_ASSIGN", DispatchTaskStatus.ASSIGNED);
        assertOperationDenied("AUTO_ASSIGN", DispatchTaskStatus.EXECUTING);
        assertOperationDenied("AUTO_ASSIGN", DispatchTaskStatus.SUCCESS);
        assertOperationDenied("AUTO_ASSIGN", DispatchTaskStatus.FAILED);
        assertOperationDenied("AUTO_ASSIGN", DispatchTaskStatus.CANCELLED);
    }

    @Test
    void manualAssignShouldRequirePendingOrManualPending() {
        assertOperationAllowed("MANUAL_ASSIGN", DispatchTaskStatus.PENDING);
        assertOperationAllowed("MANUAL_ASSIGN", DispatchTaskStatus.MANUAL_PENDING);
        assertOperationDenied("MANUAL_ASSIGN", DispatchTaskStatus.ASSIGNED);
        assertOperationDenied("MANUAL_ASSIGN", DispatchTaskStatus.ASSIGNING);
    }

    @Test
    void cancelShouldBeAllowedInAllNonTerminalStates() {
        for (DispatchTaskStatus status : List.of(
                DispatchTaskStatus.PENDING, DispatchTaskStatus.MANUAL_PENDING,
                DispatchTaskStatus.ASSIGNING, DispatchTaskStatus.ASSIGNED, DispatchTaskStatus.EXECUTING)) {
            assertTrue(status.canTransitionTo(DispatchTaskStatus.CANCELLED), status + " 应可取消");
            assertOperationAllowed("CANCEL", status);
        }
        assertOperationDenied("CANCEL", DispatchTaskStatus.SUCCESS);
        assertOperationDenied("CANCEL", DispatchTaskStatus.FAILED);
        assertOperationDenied("CANCEL", DispatchTaskStatus.CANCELLED);
    }

    @Test
    void vehicleReportTransitionsShouldFollowContract() {
        // START_EXECUTE：ASSIGNED -> EXECUTING
        assertTrue(DispatchTaskStatus.ASSIGNED.canTransitionTo(DispatchTaskStatus.EXECUTING));
        assertFalse(DispatchTaskStatus.EXECUTING.canTransitionTo(DispatchTaskStatus.EXECUTING));
        // FINISH：ASSIGNED/EXECUTING -> SUCCESS（跳过开始执行也允许收尾）
        assertTrue(DispatchTaskStatus.ASSIGNED.canTransitionTo(DispatchTaskStatus.SUCCESS));
        assertTrue(DispatchTaskStatus.EXECUTING.canTransitionTo(DispatchTaskStatus.SUCCESS));
        // REPORT_FAIL：EXECUTING -> FAILED，且 FAILED 为终态
        assertTrue(DispatchTaskStatus.EXECUTING.canTransitionTo(DispatchTaskStatus.FAILED));
        assertFalse(DispatchTaskStatus.ASSIGNED.canTransitionTo(DispatchTaskStatus.FAILED));
    }

    @Test
    void manualTakeoverAndReassignShouldFollowContract() {
        // UNASSIGN：ASSIGNED -> PENDING（释放车辆重新排队）
        assertTrue(DispatchTaskStatus.ASSIGNED.canTransitionTo(DispatchTaskStatus.PENDING));
        assertFalse(DispatchTaskStatus.EXECUTING.canTransitionTo(DispatchTaskStatus.PENDING));
        // REASSIGN：ASSIGNED -> ASSIGNED 换车自环
        assertTrue(DispatchTaskStatus.ASSIGNED.canTransitionTo(DispatchTaskStatus.ASSIGNED));
    }

    @Test
    void assertCanTransitionShouldRejectIllegalJumpWithReason() {
        DispatchTaskEntity executing = task(DispatchTaskStatus.EXECUTING);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stateService.assertCanTransition(executing, DispatchTaskStatus.ASSIGNED));
        assertEquals("DISPATCH_TASK_STATUS_INVALID", ex.getCode());
    }

    private void assertOperationAllowed(String operation, DispatchTaskStatus current) {
        DispatchTaskEntity task = task(current);
        switch (operation) {
            case "AUTO_ASSIGN" -> stateService.assertCanAutoAssign(task);
            case "MANUAL_ASSIGN" -> stateService.assertCanManualAssign(task);
            case "CANCEL" -> stateService.assertCanCancel(task);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private void assertOperationDenied(String operation, DispatchTaskStatus current) {
        DispatchTaskEntity task = task(current);
        switch (operation) {
            case "AUTO_ASSIGN" -> assertThrows(BusinessException.class, () -> stateService.assertCanAutoAssign(task));
            case "MANUAL_ASSIGN" -> assertThrows(BusinessException.class, () -> stateService.assertCanManualAssign(task));
            case "CANCEL" -> assertThrows(BusinessException.class, () -> stateService.assertCanCancel(task));
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private DispatchTaskEntity task(DispatchTaskStatus status) {
        DispatchTaskEntity entity = new DispatchTaskEntity();
        entity.setId(1L);
        entity.setStatus(status.name());
        return entity;
    }
}

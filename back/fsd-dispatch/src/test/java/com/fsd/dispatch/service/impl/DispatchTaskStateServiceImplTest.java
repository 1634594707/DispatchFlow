package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchTaskStateServiceImplTest {

    @Mock
    private DispatchTaskMapper dispatchTaskMapper;

    private DispatchTaskStateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DispatchTaskStateServiceImpl(dispatchTaskMapper);
    }

    @Test
    void assertCanAutoAssignShouldAllowManualPendingRetry() {
        DispatchTaskEntity task = task(1L, DispatchTaskStatus.MANUAL_PENDING);
        when(dispatchTaskMapper.selectById(1L)).thenReturn(task);
        assertDoesNotThrow(() -> service.assertCanAutoAssign(service.getTask(1L)));
    }

    @Test
    void assertCanAutoAssignShouldRejectAssignedTask() {
        DispatchTaskEntity task = task(2L, DispatchTaskStatus.ASSIGNED);
        when(dispatchTaskMapper.selectById(2L)).thenReturn(task);
        assertThrows(BusinessException.class, () -> service.assertCanAutoAssign(service.getTask(2L)));
    }

    private static DispatchTaskEntity task(long id, DispatchTaskStatus status) {
        DispatchTaskEntity entity = new DispatchTaskEntity();
        entity.setId(id);
        entity.setStatus(status.name());
        entity.setDeleted(0);
        return entity;
    }
}

package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchExceptionResolveRequest;
import com.fsd.dispatch.entity.DispatchExceptionRecordEntity;
import com.fsd.dispatch.event.DispatchEventPublisher;
import com.fsd.dispatch.mapper.DispatchExceptionRecordMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DispatchExceptionServiceImplTest {

    @Mock
    private DispatchExceptionRecordMapper exceptionRecordMapper;
    @Mock
    private DispatchEventPublisher eventPublisher;
    @Mock
    private com.fsd.dispatch.service.DispatchTaskOperateLogService operateLogService;

    @InjectMocks
    private DispatchExceptionServiceImpl dispatchExceptionService;

    @Test
    void recordExceptionShouldSkipDuplicateOpenRecord() {
        when(exceptionRecordMapper.selectCount(any())).thenReturn(1L);

        dispatchExceptionService.recordException(10L, 20L, 30L, "AUTO_ASSIGN_NO_VEHICLE", "duplicate");

        verify(exceptionRecordMapper, never()).insert(any(DispatchExceptionRecordEntity.class));
        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void resolveExceptionShouldMarkResolved() {
        DispatchExceptionRecordEntity entity = new DispatchExceptionRecordEntity();
        entity.setId(1L);
        entity.setTaskId(100L);
        entity.setExceptionStatus("OPEN");
        when(exceptionRecordMapper.selectById(1L)).thenReturn(entity);

        DispatchExceptionResolveRequest request = new DispatchExceptionResolveRequest();
        request.setResolverId("u1");
        request.setResolverName("dispatcher");
        request.setAction("MARK_FAILED");
        request.setRemark("done");

        dispatchExceptionService.resolveException(1L, request);

        verify(exceptionRecordMapper).updateById(any(DispatchExceptionRecordEntity.class));
        verify(eventPublisher).publish(any(), any(), any());
    }

    @Test
    void resolveExceptionShouldRejectResolvedRecord() {
        DispatchExceptionRecordEntity entity = new DispatchExceptionRecordEntity();
        entity.setId(2L);
        entity.setExceptionStatus("RESOLVED");
        when(exceptionRecordMapper.selectById(2L)).thenReturn(entity);

        DispatchExceptionResolveRequest request = new DispatchExceptionResolveRequest();
        request.setResolverId("u1");
        request.setResolverName("dispatcher");
        request.setAction("MARK_FAILED");
        request.setRemark("done");

        assertThrows(BusinessException.class, () -> dispatchExceptionService.resolveException(2L, request));
    }
}

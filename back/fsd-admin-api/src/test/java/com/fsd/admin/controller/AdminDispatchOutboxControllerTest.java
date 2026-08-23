package com.fsd.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.admin.vo.AdminDispatchDeadLetterResponse;
import com.fsd.common.exception.BusinessException;
import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.event.DispatchEventOutboxService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class AdminDispatchOutboxControllerTest {

    @Mock
    private DispatchEventOutboxService outboxService;

    @InjectMocks
    private AdminDispatchOutboxController controller;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setAttribute("fsd.admin.role", "ADMIN");
        request.setAttribute("fsd.admin.userId", 1L);
        request.setAttribute("fsd.admin.username", "admin");
    }

    @Test
    void shouldReturnDeadLettersWithoutPayload() {
        DispatchEventOutboxEntity entity = new DispatchEventOutboxEntity();
        entity.setId(1L);
        entity.setEventId("evt-1");
        entity.setEventType("dispatch.task.failed");
        entity.setBusinessKey("task-10");
        entity.setPayload("{\"secret\":\"not-exposed\"}");
        entity.setRetryCount(5);
        entity.setLastError("broker unavailable");
        when(outboxService.listDeadLetterEvents(50)).thenReturn(List.of(entity));

        ApiResponse<List<AdminDispatchDeadLetterResponse>> response =
                controller.listDeadLetters(50, request);

        assertEquals(1, response.getData().size());
        assertEquals("evt-1", response.getData().getFirst().getEventId());
        assertEquals("broker unavailable", response.getData().getFirst().getLastError());
        verify(outboxService).listDeadLetterEvents(50);
    }

    @Test
    void shouldBoundLimitAndRequireAdmin() {
        controller.listDeadLetters(500, request);
        verify(outboxService).listDeadLetterEvents(200);

        request.setAttribute("fsd.admin.role", "OPERATOR");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> controller.listDeadLetters(50, request));
        assertEquals("ADMIN_FORBIDDEN", ex.getCode());
    }
}

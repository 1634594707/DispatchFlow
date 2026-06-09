package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fsd.admin.config.AdminSseProperties;
import com.fsd.common.exception.BusinessException;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class AdminDispatchStreamServiceImplTest {

    @Test
    void createStreamShouldRejectWhenConnectionLimitReached() {
        AdminSseProperties properties = new AdminSseProperties();
        properties.setMaxConnections(1);
        AdminDispatchStreamServiceImpl service = new AdminDispatchStreamServiceImpl(properties);
        service.createStream(null, 1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createStream(null, 1L));

        assertEquals("SSE_CONNECTION_LIMIT_EXCEEDED", ex.getCode());
    }

    @Test
    void broadcastShouldOnlySendToMatchingPark() {
        AdminSseProperties properties = new AdminSseProperties();
        AdminDispatchStreamServiceImpl service = new AdminDispatchStreamServiceImpl(properties);
        CapturingEmitter parkOneEmitter = new CapturingEmitter();
        CapturingEmitter parkTwoEmitter = new CapturingEmitter();
        service.registerEmitterForTest(parkOneEmitter, 1L);
        service.registerEmitterForTest(parkTwoEmitter, 2L);

        service.broadcast("workbench", Map.of("parkId", 1L));

        assertEquals(1, parkOneEmitter.sendCount);
        assertEquals(0, parkTwoEmitter.sendCount);
    }

    private static class CapturingEmitter extends SseEmitter {
        private int sendCount;

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            sendCount++;
        }
    }
}

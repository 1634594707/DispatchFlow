package com.fsd.dispatch.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.config.DispatchOutboxProperties;
import com.fsd.dispatch.entity.DispatchEventOutboxEntity;
import com.fsd.dispatch.event.impl.DispatchEventOutboxServiceImpl;
import com.fsd.dispatch.mapper.DispatchEventOutboxMapper;
import com.fsd.dispatch.metrics.DispatchOutboxMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 路线图 4.2：Outbox 投递四类场景测试。
 *
 * <ol>
 *   <li>数据库事务提交后才发送（事务内只落 PENDING 行，afterCommit 触发领取与投递）；</li>
 *   <li>发送失败按 eventId+claimToken fencing 标记 FAILED 进入重试；</li>
 *   <li>进程重启恢复：租约过期的 PROCESSING 事件被重新领取，未过期租约不重复投递；</li>
 *   <li>重复消息：同一事件第二次领取返回 null，不会重复发送。</li>
 * </ol>
 */
class DispatchEventOutboxDeliveryScenariosTest {

    private RabbitTemplate rabbitTemplate;
    private DispatchEventOutboxService outboxService;

    @BeforeEach
    void setUp() {
        rabbitTemplate = mock(RabbitTemplate.class);
        outboxService = mock(DispatchEventOutboxService.class);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /** 场景 1：事务提交后发送。 */
    @Test
    void shouldSendOnlyAfterTransactionCommit() {
        RabbitDispatchEventPublisher publisher = new RabbitDispatchEventPublisher(
                rabbitTemplate, outboxService, new DispatchOutboxMetrics(new SimpleMeterRegistry()));
        when(outboxService.claimEvent(anyString())).thenReturn("lease-commit");

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        publisher.publish("TASK_ASSIGNED", "task-1", Map.of("k", "v"));

        // 事务内：仅落 outbox，绝不直接发送
        verify(outboxService).savePending(any(DispatchDomainEvent.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(DispatchMessagingConfig.DISPATCH_EXCHANGE), eq("TASK_ASSIGNED"), payloadCaptor.capture());
        assertTrue(payloadCaptor.getValue() instanceof DispatchDomainEvent);
        verify(outboxService).markPublished(anyString(), eq("lease-commit"));
    }

    /** 场景 2：发送失败进入 fencing 保护的失败重试。 */
    @Test
    void publishFailureShouldMarkFailedWithFencingToken() {
        RabbitDispatchEventPublisher publisher = new RabbitDispatchEventPublisher(
                rabbitTemplate, outboxService, new DispatchOutboxMetrics(new SimpleMeterRegistry()));
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        DispatchDomainEvent event = DispatchDomainEvent.builder()
                .eventId("evt-fail")
                .eventType("TASK_ASSIGNED")
                .outboxClaimToken("lease-fail")
                .build();
        publisher.publishEvent(event);

        verify(outboxService).markFailed("evt-fail", "broker down", "lease-fail");
        verify(outboxService, never()).markPublished(anyString(), anyString());
    }

    /** 场景 3：进程重启后过期租约可恢复领取，未过期租约不重复投递。 */
    @Test
    void restartShouldReclaimExpiredLeaseButNotFreshLease() {
        DispatchEventOutboxMapper mapper = mock(DispatchEventOutboxMapper.class);
        DispatchOutboxProperties properties = new DispatchOutboxProperties();
        DispatchEventOutboxServiceImpl service = new DispatchEventOutboxServiceImpl(
                mapper, new ObjectMapper(), properties, new DispatchOutboxMetrics(new SimpleMeterRegistry()));

        LocalDateTime now = LocalDateTime.now();
        DispatchEventOutboxEntity expired = processing("evt-restart", now.minusSeconds(3600));
        DispatchEventOutboxEntity fresh = processing("evt-active", now.plusSeconds(3600));
        Page<DispatchEventOutboxEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(expired, fresh));
        when(mapper.selectPage(any(Page.class), any())).thenReturn(page);
        // 过期租约条件更新成功；未过期租约条件更新影响 0 行
        when(mapper.claimIfAvailable(eq(expired.getId()), anyString(), any(now.getClass()), any(now.getClass()), any(now.getClass())))
                .thenReturn(1);
        when(mapper.claimIfAvailable(eq(fresh.getId()), anyString(), any(now.getClass()), any(now.getClass()), any(now.getClass())))
                .thenReturn(0);

        List<DispatchEventOutboxEntity> claimed = service.claimRetryableEvents(50);

        assertEquals(1, claimed.size());
        assertEquals("evt-restart", claimed.get(0).getEventId());
        assertEquals("PROCESSING", claimed.get(0).getStatus());
        assertTrue(claimed.get(0).getLeaseUntil().isAfter(now));
    }

    /** 场景 4：领取失败（已被其他实例消费的重复消息）不会再次发送。 */
    @Test
    void duplicateDeliveryShouldSendExactlyOnce() {
        RabbitDispatchEventPublisher publisher = new RabbitDispatchEventPublisher(
                rabbitTemplate, outboxService, new DispatchOutboxMetrics(new SimpleMeterRegistry()));
        TransactionSynchronizationManager.setActualTransactionActive(false);

        // 第一次投递：领取成功并发送一次
        when(outboxService.claimEvent(anyString())).thenReturn("lease-a");
        publisher.publish("TASK_ASSIGNED", "task-dup-1", Map.of("k", "v"));

        ArgumentCaptor<Object> firstPayload = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(DispatchMessagingConfig.DISPATCH_EXCHANGE), eq("TASK_ASSIGNED"), firstPayload.capture());
        DispatchDomainEvent sentEvent = (DispatchDomainEvent) firstPayload.getValue();
        verify(outboxService).markPublished(sentEvent.getEventId(), "lease-a");

        // 重复消息：同一事件再次触发，但领取被其他实例抢占（返回 null）——不得重发
        when(outboxService.claimEvent(sentEvent.getEventId())).thenReturn(null);
        publisher.claimAndPublish(sentEvent);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(DispatchMessagingConfig.DISPATCH_EXCHANGE), eq("TASK_ASSIGNED"), any(Object.class));
        verify(outboxService, never()).markFailed(anyString(), anyString());
    }

    private DispatchEventOutboxEntity processing(String eventId, LocalDateTime leaseUntil) {
        DispatchEventOutboxEntity entity = new DispatchEventOutboxEntity();
        entity.setId(Math.abs(UUID.randomUUID().getLeastSignificantBits()));
        entity.setEventId(eventId);
        entity.setStatus("PROCESSING");
        entity.setClaimToken("old-token");
        entity.setLeaseUntil(leaseUntil);
        entity.setCreatedAt(now().minusMinutes(5));
        return entity;
    }

    private LocalDateTime now() {
        return LocalDateTime.now();
    }
}

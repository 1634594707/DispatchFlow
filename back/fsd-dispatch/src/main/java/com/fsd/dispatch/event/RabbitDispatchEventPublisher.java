package com.fsd.dispatch.event;

import com.fsd.dispatch.config.DispatchMessagingConfig;
import com.fsd.dispatch.metrics.DispatchOutboxMetrics;
import io.micrometer.core.instrument.Timer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitDispatchEventPublisher implements DispatchEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final DispatchEventOutboxService outboxService;
    private final DispatchOutboxMetrics metrics;

    public RabbitDispatchEventPublisher(RabbitTemplate rabbitTemplate,
                                        DispatchEventOutboxService outboxService,
                                        DispatchOutboxMetrics metrics) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxService = outboxService;
        this.metrics = metrics;
    }

    @Override
    public void publish(String eventType, String businessKey, Object payload) {
        DispatchDomainEvent event = DispatchDomainEvent.of(eventType, businessKey, payload);
        outboxService.savePending(event);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    claimAndPublish(event);
                }
            });
            return;
        }
        claimAndPublish(event);
    }

    /** 领取后投递：领取失败（重复消息/已被其他实例抢占）直接放弃发送（包可见便于测试投递场景）。 */
    void claimAndPublish(DispatchDomainEvent event) {
        String claimToken = outboxService.claimEvent(event.getEventId());
        if (claimToken == null) {
            return;
        }
        event.setOutboxClaimToken(claimToken);
        publishEvent(event);
    }

    @Override
    public void publishEvent(DispatchDomainEvent event) {
        Timer.Sample sample = metrics.startPublishTimer();
        try {
            rabbitTemplate.convertAndSend(
                    DispatchMessagingConfig.DISPATCH_EXCHANGE,
                    event.getEventType(),
                    event
            );
            outboxService.markPublished(event.getEventId(), event.getOutboxClaimToken());
        } catch (Exception ex) {
            outboxService.markFailed(event.getEventId(), ex.getMessage(), event.getOutboxClaimToken());
        } finally {
            metrics.recordPublishLatency(sample);
        }
    }
}

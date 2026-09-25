package com.fsd.dispatch.event;

public interface DispatchEventConsumeIdempotencyService {

    boolean markIfFirstConsume(String eventId);

    /**
     * 只读判重，供"先投递、成功后再落标记"的消费者使用。
     *
     * <p>与 {@link #markIfFirstConsume} 的区别就是这条语义差别：边判边占会把"已判重但尚未投递成功"
     * 的事件也占住，进程在投递前崩溃 ⇒ 重投时被当重复跳过 ⇒ 事件静默消失。webhook 那条路已经踩过
     * 这个坑（见 {@code WebhookDispatchListener}），所以新消费者一律用 {@code isConsumed + markConsumed}。</p>
     */
    boolean isConsumed(String eventId);

    void markConsumed(String eventId);
}

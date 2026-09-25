package com.fsd.dispatch.integration;

/**
 * 至少有一个匹配的订阅**没有收到**这次投递（HTTP 失败、SSRF 拦截、或订阅已被熔断）。
 *
 * <p>存在的唯一理由：{@code WebhookDispatchListener} 是手动 ack，必须知道投递结果才能决定
 * ack 还是 nack。之前 {@code deliver()} 把 HTTP 丢给线程池后立即返回 ⇒ 监听器永远 ack，
 * 失败的 webhook 只在 {@code t_webhook_delivery_log} 留一行，消息本身从 broker 消失，
 * DLQ 一条也收不到（§13.56 端到端实测）。</p>
 *
 * <p>抛在**所有订阅都尝试过之后**：中途抛出会让后面的订阅永远收不到这个事件。逐次尝试的原因
 * 已经落在 {@code t_webhook_delivery_log}，这里只带聚合计数。</p>
 */
public class WebhookDeliveryException extends RuntimeException {

    /** {@code cause} 只在异常从投递内部冒出来时带上；HTTP 失败的原因已逐条落库，不必重复。 */
    public WebhookDeliveryException(int matchedSubscriptions, int failedSubscriptions, Throwable cause) {
        super("Webhook delivery failed for " + failedSubscriptions + " of " + matchedSubscriptions
                + " matched subscriptions", cause);
    }
}

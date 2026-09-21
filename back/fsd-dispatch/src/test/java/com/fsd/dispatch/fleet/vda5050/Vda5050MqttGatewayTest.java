package com.fsd.dispatch.fleet.vda5050;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §7.2「MQTT 重连不重订阅」：自动重连只把 TCP 会话连回来，订阅不会自己恢复。
 * 旧实现连 {@code connectComplete} 都没有 ⇒ 断网一次后车辆状态永久进不来，而且没有任何指标能看出来。
 */
class Vda5050MqttGatewayTest {

    private static final String INTERFACE_NAME = "if1";
    private static final int QOS = 1;

    private Vda5050MqttProperties properties;
    private MqttClient client;
    private SimpleMeterRegistry registry;
    private Vda5050MqttGateway gateway;

    @BeforeEach
    void setUp() {
        properties = mock(Vda5050MqttProperties.class);
        when(properties.getInterfaceName()).thenReturn(INTERFACE_NAME);
        when(properties.getQos()).thenReturn(QOS);
        client = mock(MqttClient.class);
        registry = new SimpleMeterRegistry();
        gateway = new Vda5050MqttGateway(properties, mock(Vda5050StateIngestService.class), registry);
        gateway.client = client;
    }

    private String expectedTopic() {
        return Vda5050TopicHelper.stateSubscription(INTERFACE_NAME);
    }

    @Test
    @DisplayName("重连完成后必须重新订阅，并记一次成功")
    void reconnectMustResubscribe() throws Exception {
        gateway.connectComplete(true, "ssl://broker:8883");

        verify(client).subscribe(eq(expectedTopic()), eq(QOS));
        assertEquals(1D, registry.get(Vda5050MqttGateway.CONNECTION_METRIC)
                .tag("event", "resubscribed").counter().count());
    }

    @Test
    @DisplayName("首次连接由启动路径订阅，connectComplete(false) 不重复订阅")
    void firstConnectDoesNotDoubleSubscribe() throws Exception {
        gateway.connectComplete(false, "ssl://broker:8883");

        verify(client, never()).subscribe(eq(expectedTopic()), anyInt());
    }

    @Test
    @DisplayName("订阅失败不得把异常抛回 Paho 回调线程，且必须留下失败计数")
    void subscribeFailureIsCountedAndNotRethrown() throws Exception {
        org.mockito.Mockito.doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION))
                .when(client).subscribe(expectedTopic(), QOS);

        assertDoesNotThrow(() -> gateway.connectComplete(true, "ssl://broker:8883"));

        assertEquals(1D, registry.get(Vda5050MqttGateway.CONNECTION_METRIC)
                .tag("event", "resubscribe_failed").counter().count());
    }

    @Test
    @DisplayName("掉线本身要可观测：以前只有一行 WARN，没人会去翻日志")
    void connectionLostIsCounted() {
        gateway.connectionLost(new IllegalStateException("network down"));

        assertEquals(1D, registry.get(Vda5050MqttGateway.CONNECTION_METRIC)
                .tag("event", "lost").counter().count());
    }
}

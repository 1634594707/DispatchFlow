package com.fsd.dispatch.fleet.vda5050;

import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Vda5050MqttGateway implements MqttCallback {

    private static final Logger log = LoggerFactory.getLogger(Vda5050MqttGateway.class);

    private final Vda5050MqttProperties properties;
    private final Vda5050StateIngestService stateIngestService;
    private MqttClient client;

    public Vda5050MqttGateway(Vda5050MqttProperties properties, Vda5050StateIngestService stateIngestService) {
        this.properties = properties;
        this.stateIngestService = stateIngestService;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public void publish(String topic, String payload) {
        if (!isConnected()) {
            log.warn("Skip VDA5050 publish; MQTT disconnected: {}", topic);
            return;
        }
        try {
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(properties.getQos());
            client.publish(topic, message);
        } catch (MqttException ex) {
            log.error("VDA5050 publish failed for {}: {}", topic, ex.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("VDA5050 MQTT connection lost: {}", cause != null ? cause.getMessage() : "unknown");
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        stateIngestService.ingestStateTopic(topic, payload);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // no-op
    }

    @PreDestroy
    public void shutdown() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            client.close();
        } catch (MqttException ex) {
            log.debug("VDA5050 MQTT shutdown: {}", ex.getMessage());
        }
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void start() {
        if (properties.isEnabled()) {
            connect();
        } else {
            log.info("VDA5050 MQTT integration disabled (fsd.vda5050.mqtt.enabled=false)");
        }
    }

    private void connect() {
        try {
            client = new MqttClient(properties.getBrokerUrl(), properties.getClientId(), new MemoryPersistence());
            client.setCallback(this);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
            options.setKeepAliveInterval(properties.getKeepAliveSeconds());
            client.connect(options);
            String subscription = Vda5050TopicHelper.stateSubscription(properties.getInterfaceName());
            client.subscribe(subscription, properties.getQos());
            log.info("VDA5050 MQTT connected to {} subscribed {}", properties.getBrokerUrl(), subscription);
        } catch (MqttException ex) {
            log.warn("VDA5050 MQTT broker unavailable at startup: {}", ex.getMessage());
            client = null;
        }
    }
}

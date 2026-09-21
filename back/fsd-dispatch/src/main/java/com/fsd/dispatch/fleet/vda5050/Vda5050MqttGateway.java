package com.fsd.dispatch.fleet.vda5050;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Vda5050MqttGateway implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(Vda5050MqttGateway.class);

    /** 重订阅的成功/失败与掉线次数（§7.2：静默失聪必须先变成可见）。 */
    static final String CONNECTION_METRIC = "dispatchflow.vda5050.mqtt.connection";

    private final Vda5050MqttProperties properties;
    private final Vda5050StateIngestService stateIngestService;
    private final MeterRegistry registry;
    MqttClient client;

    public Vda5050MqttGateway(Vda5050MqttProperties properties,
                              Vda5050StateIngestService stateIngestService,
                              MeterRegistry registry) {
        this.properties = properties;
        this.stateIngestService = stateIngestService;
        this.registry = registry;
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
        registry.counter(CONNECTION_METRIC, "event", "lost").increment();
        log.warn("VDA5050 MQTT connection lost: {}（自动重连已开启，恢复时由 connectComplete 重新订阅）",
                cause != null ? cause.getMessage() : "unknown");
    }

    /**
     * §7.2「MQTT 重连不重订阅」：{@code setAutomaticReconnect(true)} 与 {@code cleanSession(true)} 的组合下，
     * Paho 只会把 TCP 会话连回来，**订阅不会自动恢复**；而旧实现只实现了 {@code MqttCallback}，
     * 连这个回调都没有 ⇒ 断网一次之后车辆状态就再也进不来，连接状态看着正常、实际是失聪。
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (!reconnect) {
            log.info("VDA5050 MQTT connected to {}", serverURI);
            return;
        }
        if (subscribeStateTopic("reconnect")) {
            registry.counter(CONNECTION_METRIC, "event", "resubscribed").increment();
        } else {
            registry.counter(CONNECTION_METRIC, "event", "resubscribe_failed").increment();
        }
    }

    private boolean subscribeStateTopic(String via) {
        String subscription = Vda5050TopicHelper.stateSubscription(properties.getInterfaceName());
        MqttClient current = client;
        if (current == null) {
            log.warn("VDA5050 MQTT 没有可用客户端，跳过订阅 (via={})", via);
            return false;
        }
        try {
            current.subscribe(subscription, properties.getQos());
            log.info("VDA5050 MQTT subscribed {} (via={}, authenticated user={})",
                    subscription, via, properties.getUsername());
            return true;
        } catch (MqttException ex) {
            // 绝不把异常抛回 Paho 回调线程：那会让客户端停在不可预期的状态，
            // 而这条路径以前既没人看见、也没人计数
            log.error("VDA5050 MQTT subscribe failed after {} for {}: {}", via, subscription, ex.getMessage());
            return false;
        }
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
            // SEC-09 fix: refuse to start with anonymous MQTT access. Operators must
            // provide credentials (and a TLS endpoint for production).
            if (properties.getUsername() == null || properties.getUsername().isBlank()
                    || properties.getPassword() == null || properties.getPassword().isBlank()) {
                log.error("VDA5050 MQTT enabled but username/password are blank. "
                        + "Refusing to connect with anonymous access (SEC-09). Set fsd.vda5050.mqtt.username/password.");
                return;
            }
            if (properties.getBrokerUrl() != null && properties.getBrokerUrl().startsWith("tcp://")
                    && !Boolean.getBoolean("fsd.vda5050.mqtt.allow-plain-tcp")) {
                log.error("VDA5050 MQTT broker URL uses plain TCP ({}). Production deployments MUST use "
                        + "ssl:// (TLS). Set -Dfsd.vda5050.mqtt.allow-plain-tcp=true to override for dev.",
                        properties.getBrokerUrl());
                return;
            }
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
            // SEC-09 fix: always supply credentials. Anonymous connections are rejected
            // in start() before reaching here.
            options.setUserName(properties.getUsername());
            options.setPassword(properties.getPassword().toCharArray());
            // SEC-09 fix: configure TLS socket factory when using ssl:// endpoints.
            String brokerUrl = properties.getBrokerUrl();
            if (brokerUrl != null && brokerUrl.startsWith("ssl://")) {
                javax.net.ssl.SSLSocketFactory socketFactory = buildTlsSocketFactory();
                if (socketFactory != null) {
                    options.setSocketFactory(socketFactory);
                }
            }
            client.connect(options);
            subscribeStateTopic("startup");
        } catch (MqttException ex) {
            log.warn("VDA5050 MQTT broker unavailable at startup: {}", ex.getMessage());
            client = null;
        }
    }

    /**
     * SEC-09: build an SSLSocketFactory from the configured CA/client cert paths. Returns
     * null to fall back to the JVM default trust store when no custom certs are configured.
     */
    private javax.net.ssl.SSLSocketFactory buildTlsSocketFactory() {
        try {
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLSv1.2");
            javax.net.ssl.TrustManager[] trustManagers = null;
            javax.net.ssl.KeyManager[] keyManagers = null;

            String caPath = properties.getCaCertPath();
            if (caPath != null && !caPath.isBlank()) {
                java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                java.security.KeyStore trustStore = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
                trustStore.load(null, null);
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(caPath))) {
                    java.security.cert.Certificate ca = cf.generateCertificate(in);
                    trustStore.setCertificateEntry("mqtt-ca", ca);
                }
                javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                        javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(trustStore);
                trustManagers = tmf.getTrustManagers();
            }

            String certPath = properties.getClientCertPath();
            String keyPath = properties.getClientKeyPath();
            if (certPath != null && !certPath.isBlank() && keyPath != null && !keyPath.isBlank()) {
                // Note: PEM key loading requires BouncyCastle or PKCS8 conversion. For now
                // we only support a PKCS12 keystore path via clientCertPath when mTLS is needed;
                // operators should convert PEM → PKCS12 and point clientCertPath at the .p12 file.
                java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
                try (java.io.InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(certPath))) {
                    keyStore.load(in, (properties.getPassword() == null ? "" : properties.getPassword()).toCharArray());
                }
                javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance(
                        javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
                kmf.init(keyStore, (properties.getPassword() == null ? "" : properties.getPassword()).toCharArray());
                keyManagers = kmf.getKeyManagers();
            }

            sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());
            return sslContext.getSocketFactory();
        } catch (GeneralSecurityException | IOException ex) {
            log.warn("VDA5050 MQTT TLS setup failed, falling back to JVM defaults: {}", ex.getMessage());
            return null;
        }
    }
}

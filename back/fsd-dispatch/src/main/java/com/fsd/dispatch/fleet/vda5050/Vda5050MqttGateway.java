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
            String subscription = Vda5050TopicHelper.stateSubscription(properties.getInterfaceName());
            client.subscribe(subscription, properties.getQos());
            log.info("VDA5050 MQTT connected to {} subscribed {} (authenticated user={})",
                    properties.getBrokerUrl(), subscription, properties.getUsername());
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
        } catch (Exception ex) {
            log.warn("VDA5050 MQTT TLS setup failed, falling back to JVM defaults: {}", ex.getMessage());
            return null;
        }
    }
}

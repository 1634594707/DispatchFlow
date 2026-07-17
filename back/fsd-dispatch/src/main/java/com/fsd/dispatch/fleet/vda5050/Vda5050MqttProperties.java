package com.fsd.dispatch.fleet.vda5050;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fsd.vda5050.mqtt")
public class Vda5050MqttProperties {

    private boolean enabled = false;

    private String brokerUrl = "tcp://127.0.0.1:1883";

    private String clientId = "dispatchflow-fms";

    private String interfaceName = "uagv/v2";

    private int qos = 1;

    private int connectionTimeoutSeconds = 10;

    private int keepAliveSeconds = 30;

    /**
     * SEC-09 fix: MQTT broker credentials. Both must be non-blank to enable authenticated
     * connections. When left empty the gateway will refuse to start (instead of silently
     * falling back to anonymous access).
     */
    private String username = "";

    private String password = "";

    /**
     * SEC-09 fix: path to a PEM-encoded CA bundle used to verify the broker TLS cert.
     * Only consulted when brokerUrl uses the ssl:// scheme. Empty = use the JVM trust store.
     */
    private String caCertPath = "";

    /**
     * SEC-09 fix: path to a PEM-encoded client cert/key pair for mutual TLS. Optional;
     * when set, the broker must also be configured to require client certs.
     */
    private String clientCertPath = "";

    private String clientKeyPath = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBrokerUrl() {
        return brokerUrl;
    }

    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }

    public int getQos() {
        return qos;
    }

    public void setQos(int qos) {
        this.qos = qos;
    }

    public int getConnectionTimeoutSeconds() {
        return connectionTimeoutSeconds;
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    public int getKeepAliveSeconds() {
        return keepAliveSeconds;
    }

    public void setKeepAliveSeconds(int keepAliveSeconds) {
        this.keepAliveSeconds = keepAliveSeconds;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCaCertPath() {
        return caCertPath;
    }

    public void setCaCertPath(String caCertPath) {
        this.caCertPath = caCertPath;
    }

    public String getClientCertPath() {
        return clientCertPath;
    }

    public void setClientCertPath(String clientCertPath) {
        this.clientCertPath = clientCertPath;
    }

    public String getClientKeyPath() {
        return clientKeyPath;
    }

    public void setClientKeyPath(String clientKeyPath) {
        this.clientKeyPath = clientKeyPath;
    }
}

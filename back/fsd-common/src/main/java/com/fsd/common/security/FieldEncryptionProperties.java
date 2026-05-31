package com.fsd.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fsd.security.field-encryption")
public class FieldEncryptionProperties {

    /**
     * When true and key is set, sensitive fields are encrypted at rest (AES-GCM).
     */
    private boolean enabled = false;

    /**
     * Base secret for deriving the AES-256 key. Set via FSD_FIELD_ENCRYPTION_KEY in production.
     */
    private String key = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}

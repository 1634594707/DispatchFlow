package com.fsd.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FieldEncryptionServiceTest {

    @Test
    void shouldRoundTripWhenEncryptionEnabled() {
        FieldEncryptionProperties properties = new FieldEncryptionProperties();
        properties.setEnabled(true);
        properties.setKey("unit-test-key");
        FieldEncryptionService service = new FieldEncryptionService(properties);

        String encrypted = service.encrypt("JBSWY3DPEHPK3PXP");
        assertTrue(service.isEncrypted(encrypted));
        assertNotEquals("JBSWY3DPEHPK3PXP", encrypted);
        assertEquals("JBSWY3DPEHPK3PXP", service.decrypt(encrypted));
    }

    @Test
    void shouldPassThroughWhenEncryptionDisabled() {
        FieldEncryptionProperties properties = new FieldEncryptionProperties();
        FieldEncryptionService service = new FieldEncryptionService(properties);

        assertFalse(service.isActive());
        assertEquals("plain-secret", service.encrypt("plain-secret"));
        assertEquals("plain-secret", service.decrypt("plain-secret"));
    }

    @Test
    void shouldReadLegacyPlaintextWhenEncryptionEnabled() {
        FieldEncryptionProperties properties = new FieldEncryptionProperties();
        properties.setEnabled(true);
        properties.setKey("unit-test-key");
        FieldEncryptionService service = new FieldEncryptionService(properties);

        assertEquals("legacy-secret", service.decrypt("legacy-secret"));
    }
}

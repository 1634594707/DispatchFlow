package com.fsd.bootstrap.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "fsd.security")
public class SecurityProperties {

    private final AdminSecurity admin = new AdminSecurity();
    private final VehicleGatewaySecurity vehicleGateway = new VehicleGatewaySecurity();

    @Data
    public static class AdminSecurity {
        /**
         * Production default: authentication MUST be enabled. Set to false only for
         * local development via YAML profile. SEC-02 hardening: even when disabled,
         * an explicit JVM property (-Dfsd.admin.unsafe-no-auth=true) is required.
         */
        private boolean enabled = true;
        /**
         * SEC-01 fix: static YAML token mapping removed. All admin authentication MUST
         * go through DB-backed sessions managed by AdminAuthService. This field is kept
         * only to absorb any legacy YAML entries so old configs don't fail to bind,
         * but it is no longer consulted by any interceptor.
         */
        private java.util.Map<String, String> tokens = new java.util.HashMap<>();
    }

    @Data
    public static class VehicleGatewaySecurity {
        private boolean enabled = true;
    }
}

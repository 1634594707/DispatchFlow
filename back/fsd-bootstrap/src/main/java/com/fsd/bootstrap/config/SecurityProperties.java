package com.fsd.bootstrap.config;

import java.util.HashMap;
import java.util.Map;
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
        private boolean enabled = false;
        private Map<String, String> tokens = new HashMap<>();
    }

    @Data
    public static class VehicleGatewaySecurity {
        private boolean enabled = true;
    }
}

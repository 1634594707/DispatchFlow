package com.fsd.bootstrap.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final VehicleGatewayAuthInterceptor vehicleGatewayAuthInterceptor;
    private final OpenApiAuthInterceptor openApiAuthInterceptor;

    @Value("${fsd.security.cors.allowed-origins:}")
    private String allowedOrigins;

    public WebMvcSecurityConfig(AdminAuthInterceptor adminAuthInterceptor,
                                VehicleGatewayAuthInterceptor vehicleGatewayAuthInterceptor,
                                OpenApiAuthInterceptor openApiAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.vehicleGatewayAuthInterceptor = vehicleGatewayAuthInterceptor;
        this.openApiAuthInterceptor = openApiAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(openApiAuthInterceptor)
                .addPathPatterns("/api/open/**");
        registry.addInterceptor(vehicleGatewayAuthInterceptor)
                .addPathPatterns("/api/vehicle-gateway/**");
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = (allowedOrigins == null || allowedOrigins.isBlank())
                ? new String[0]
                : Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);
        if (origins.length == 0) {
            return;
        }
        // allowedOriginPatterns (not allowedOrigins("*")) is required when allowCredentials=true
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Admin-Token", "X-Api-Key",
                        "X-Vehicle-Code", "X-Vehicle-Token", "X-Mobile-Api-Key")
                .allowCredentials(true)
                .maxAge(3600);
    }
}

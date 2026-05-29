package com.fsd.bootstrap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final VehicleGatewayAuthInterceptor vehicleGatewayAuthInterceptor;

    public WebMvcSecurityConfig(AdminAuthInterceptor adminAuthInterceptor,
                                VehicleGatewayAuthInterceptor vehicleGatewayAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
        this.vehicleGatewayAuthInterceptor = vehicleGatewayAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(vehicleGatewayAuthInterceptor)
                .addPathPatterns("/api/vehicle-gateway/**");
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/admin/**");
    }
}

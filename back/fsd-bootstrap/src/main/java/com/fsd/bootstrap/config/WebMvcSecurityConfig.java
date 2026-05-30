package com.fsd.bootstrap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final VehicleGatewayAuthInterceptor vehicleGatewayAuthInterceptor;
    private final OpenApiAuthInterceptor openApiAuthInterceptor;

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
}

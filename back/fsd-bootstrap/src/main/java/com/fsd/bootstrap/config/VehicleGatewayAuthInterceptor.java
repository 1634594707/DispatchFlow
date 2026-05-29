package com.fsd.bootstrap.config;

import com.fsd.dispatch.controller.VehicleGatewayAuthContext;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleCredentialService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class VehicleGatewayAuthInterceptor implements HandlerInterceptor {

    private final SecurityProperties securityProperties;
    private final VehicleCredentialService vehicleCredentialService;

    public VehicleGatewayAuthInterceptor(SecurityProperties securityProperties,
                                         VehicleCredentialService vehicleCredentialService) {
        this.securityProperties = securityProperties;
        this.vehicleCredentialService = vehicleCredentialService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!securityProperties.getVehicleGateway().isEnabled()) {
            String vehicleCode = request.getHeader("X-Vehicle-Code");
            if (vehicleCode != null && !vehicleCode.isBlank()) {
                VehicleGatewayAuthContext.setVehicleCode(vehicleCode);
            }
            return true;
        }
        String vehicleCode = request.getHeader("X-Vehicle-Code");
        String apiKey = request.getHeader("X-Vehicle-Token");
        VehicleEntity vehicle = vehicleCredentialService.authenticate(vehicleCode, apiKey);
        VehicleGatewayAuthContext.setVehicleCode(vehicle.getVehicleCode());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        VehicleGatewayAuthContext.clear();
    }
}

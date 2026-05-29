package com.fsd.dispatch.dispatch;

import com.fsd.common.enums.DispatchAssignFailReason;
import com.fsd.vehicle.entity.VehicleEntity;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DispatchAssignResult {

    private boolean success;

    private VehicleEntity vehicle;

    private DispatchAssignFailReason failReason;

    private String message;

    private String vehicleCode;

    private Double totalScore;

    private Double distanceScore;

    private Double socScore;

    private Double pluggedStandbyBonus;

    public static DispatchAssignResult success(VehicleEntity vehicle, String message, double totalScore,
                                               double distanceScore, double socScore, double pluggedBonus) {
        return DispatchAssignResult.builder()
                .success(true)
                .vehicle(vehicle)
                .vehicleCode(vehicle.getVehicleCode())
                .message(message)
                .totalScore(totalScore)
                .distanceScore(distanceScore)
                .socScore(socScore)
                .pluggedStandbyBonus(pluggedBonus)
                .build();
    }

    public static DispatchAssignResult failure(DispatchAssignFailReason reason, String message) {
        return DispatchAssignResult.builder()
                .success(false)
                .failReason(reason)
                .message(message)
                .build();
    }
}

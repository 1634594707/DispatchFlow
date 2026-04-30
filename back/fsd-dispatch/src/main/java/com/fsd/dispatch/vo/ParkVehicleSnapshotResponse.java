package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkVehicleSnapshotResponse {

    private Long vehicleId;

    private String vehicleCode;

    private String vehicleName;

    private String onlineStatus;

    private String dispatchStatus;

    private Long currentTaskId;

    private Long currentOrderId;

    private Integer batteryLevel;

    private BigDecimal x;

    private BigDecimal y;

    private String runtimeStage;

    private String targetCode;

    private String targetType;

    private Boolean charging;

    private Boolean lowBattery;

    private List<ParkPointResponse> trajectory;
}

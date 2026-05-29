package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleCommandResponse {

    private Long commandId;

    private String commandType;

    private String commandStatus;

    private Long taskId;

    private Long orderId;

    private Long pickupStationId;

    private Long dropoffStationId;

    private String pickupStationCode;

    private String dropoffStationCode;

    private LocalDateTime issuedAt;
}

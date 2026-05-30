package com.fsd.admin.vo;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminVehicleCredentialResponse {

    private Long id;

    private Long vehicleId;

    private String apiKey;

    private String status;

    private LocalDateTime createdAt;
}

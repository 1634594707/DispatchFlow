package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDigitalTwinSimulateResponse {

    private String scenario;

    private String summary;

    private Integer estimatedMinutes;

    private Integer recommendedVehicleCount;

    private List<String> notes;
}

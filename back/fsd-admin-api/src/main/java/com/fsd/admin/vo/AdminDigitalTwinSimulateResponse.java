package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDigitalTwinSimulateResponse {

    private String scenario;

    /** ENGINE = 派车评分+路网 dry-run；ESTIMATE = 规则估算 */
    private String simulationMode;

    private String summary;

    private Integer estimatedMinutes;

    private Integer recommendedVehicleCount;

    private List<String> notes;
}

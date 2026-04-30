package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkStationResponse {

    private Long stationId;

    private String stationCode;

    private String stationName;

    private BigDecimal x;

    private BigDecimal y;

    private String area;
}

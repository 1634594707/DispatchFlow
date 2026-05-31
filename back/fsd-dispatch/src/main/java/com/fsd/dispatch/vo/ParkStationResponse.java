package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkStationResponse {

    private Long parkId;

    private String parkCode;

    private Long stationId;

    private String stationCode;

    private String stationName;

    private String stationType;

    private BigDecimal x;

    private BigDecimal y;

    /** 可选 GCJ-02，M4 物流矩阵 N-1 评分。 */
    private BigDecimal coordLng;

    private BigDecimal coordLat;

    private String area;
}

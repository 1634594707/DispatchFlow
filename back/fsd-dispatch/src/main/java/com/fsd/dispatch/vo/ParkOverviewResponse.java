package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkOverviewResponse {

    private Long parkId;

    private String parkCode;

    private String parkName;

    private BigDecimal centerLng;

    private BigDecimal centerLat;

    private String mapProvider;

    private long vehicleCount;

    private long onlineCount;

    private long busyCount;
}

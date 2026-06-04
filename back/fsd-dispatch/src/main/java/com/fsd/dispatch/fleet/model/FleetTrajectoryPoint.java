package com.fsd.dispatch.fleet.model;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetTrajectoryPoint {

    private String code;

    private BigDecimal x;

    private BigDecimal y;

    private BigDecimal longitude;

    private BigDecimal latitude;
}

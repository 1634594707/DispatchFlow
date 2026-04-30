package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkPointResponse {

    private String code;

    private BigDecimal x;

    private BigDecimal y;
}

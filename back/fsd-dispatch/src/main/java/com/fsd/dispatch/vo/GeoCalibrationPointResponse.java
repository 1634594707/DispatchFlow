package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeoCalibrationPointResponse {

    private String code;

    private String name;

    private BigDecimal parkX;

    private BigDecimal parkY;

    private BigDecimal longitude;

    private BigDecimal latitude;
}

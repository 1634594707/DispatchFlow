package com.fsd.dispatch.vo;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GeoTransformResponse {

    private BigDecimal parkX;

    private BigDecimal parkY;

    private BigDecimal longitude;

    private BigDecimal latitude;
}

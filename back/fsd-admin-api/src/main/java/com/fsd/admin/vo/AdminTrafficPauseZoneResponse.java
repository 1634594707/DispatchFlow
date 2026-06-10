package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTrafficPauseZoneResponse {

    private Double minX;

    private Double minY;

    private Double maxX;

    private Double maxY;

    private String label;
}

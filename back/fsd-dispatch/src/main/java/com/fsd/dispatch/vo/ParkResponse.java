package com.fsd.dispatch.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ParkResponse {

    private Long parkId;

    private String parkCode;

    private String parkName;

    private Integer mapWidth;

    private Integer mapHeight;

    private boolean defaultPark;
}

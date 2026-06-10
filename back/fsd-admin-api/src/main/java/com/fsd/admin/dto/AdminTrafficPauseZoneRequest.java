package com.fsd.admin.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AdminTrafficPauseZoneRequest {

    @NotNull
    private Long parkId;

    @NotNull
    private Double minX;

    @NotNull
    private Double minY;

    @NotNull
    private Double maxX;

    @NotNull
    private Double maxY;

    private String label;
}

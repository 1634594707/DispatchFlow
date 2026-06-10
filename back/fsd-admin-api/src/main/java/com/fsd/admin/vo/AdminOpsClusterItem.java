package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminOpsClusterItem {

    private String gridKey;

    private int vehicleCount;

    private double centerX;

    private double centerY;

    private int minSoc;
}

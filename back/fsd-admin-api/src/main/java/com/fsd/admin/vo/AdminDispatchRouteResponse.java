package com.fsd.admin.vo;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminDispatchRouteResponse {

    private Long id;

    private Long parkId;

    private String routeCode;

    private String routeName;

    private String status;

    private LocalTime serviceStartTime;

    private LocalTime serviceEndTime;

    private String requiredVehicleType;

    private Integer maxConcurrentTasks;

    private Integer activeTaskCount;

    private List<AdminRouteStationResponse> stations;

    private String remark;

    private LocalDateTime updatedAt;
}

package com.fsd.admin.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminHubQueuedTaskResponse {

    private Long taskId;

    private String taskNo;

    private Long orderId;

    private String status;

    private Long hubStationId;

    private String hubStationName;

    private String suggestion;
}

package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminHubOverviewResponse {

    private List<AdminHubStationStatusResponse> hubs;

    private List<AdminHubQueuedTaskResponse> queuedTasks;
}

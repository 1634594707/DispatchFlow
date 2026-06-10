package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminOpsSnapshotResponse {

    private List<AdminOpsClusterItem> lowBatteryClusters;

    private List<AdminOpsVehicleItem> offlineVehicles;

    private List<AdminHubQueuedTaskResponse> hubQueuedTasks;
}

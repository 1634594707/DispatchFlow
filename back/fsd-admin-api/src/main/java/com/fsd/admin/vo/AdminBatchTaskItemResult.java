package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminBatchTaskItemResult {

    private Long taskId;

    private String taskNo;

    private boolean success;

    private String message;

    private String status;

    private Long vehicleId;

    private String reasonCode;

    private String reasonMessage;

    private List<String> suggestions;
}

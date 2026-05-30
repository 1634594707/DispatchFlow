package com.fsd.admin.vo;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminBatchTaskResultResponse {

    private int total;

    private int successCount;

    private int failureCount;

    private List<AdminBatchTaskItemResult> results;
}

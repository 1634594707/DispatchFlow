package com.fsd.admin.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 批量操作统一结果契约（路线图 2.2）：总数、成功数、失败数、逐条结果（含失败原因）、
 * 可重试任务列表和操作时间。
 */
@Data
@Builder
public class AdminBatchTaskResultResponse {

    /** 操作类型：AUTO_ASSIGN / CANCEL / REASSIGN / UNASSIGN。 */
    private String operation;

    private int total;

    private int successCount;

    private int failureCount;

    private List<AdminBatchTaskItemResult> results;

    /** 失败原因属瞬时类、允许直接重试的任务 ID 列表。 */
    private List<Long> retryableTaskIds;

    /** 操作完成时间。 */
    private LocalDateTime operatedAt;
}

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

    /** 失败原因是否为瞬时类（锁冲突/无车/系统错误等），可整批重试；状态类拒绝不可重试。 */
    private boolean retryable;
}

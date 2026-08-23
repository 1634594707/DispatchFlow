package com.fsd.dispatch.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 统一任务时间线读取模型（路线图 3.2）。
 *
 * <p>串联订单创建、派车、车辆回报、执行、异常、重试、改派、取消和完成事件，
 * 每个条目包含进入时间、来源、前后状态与失败原因。</p>
 */
@Data
@Builder
public class TaskTimelineResponse {

    private Long taskId;

    private String taskNo;

    private String taskStatus;

    private Long orderId;

    private List<Entry> entries;

    @Data
    @Builder
    public static class Entry {

        /** 事件发生时间（升序排列）。 */
        private LocalDateTime time;

        /** 事件类型：CREATE_ORDER / CREATE_TASK / AUTO_ASSIGN / MANUAL_ASSIGN / START_EXECUTE /
         * FINISH_SUCCESS / FINISH_FAILED / TASK_RETRY / ENTER_MANUAL_PENDING / UNASSIGN_TASK /
         * REASSIGN / CANCEL_TASK / RESET_FOR_AUTO_ASSIGN / ISSUE_COMMAND / COMMAND_FAILED /
         * EXCEPTION_RESOLVE / EXCEPTION_RAISED / EXCEPTION_RESOLVED。 */
        private String eventType;

        private String beforeStatus;

        private String afterStatus;

        /** 事件来源：SYSTEM / DISPATCHER / VEHICLE / MOBILE。 */
        private String source;

        private String operatorName;

        private String message;

        /** 失败原因（仅失败类事件）。 */
        private String failReason;

        /** 是否异常类条目（前端高亮）。 */
        private Boolean exception;

        /** 异常严重级别（仅异常条目）。 */
        private String severity;
    }
}

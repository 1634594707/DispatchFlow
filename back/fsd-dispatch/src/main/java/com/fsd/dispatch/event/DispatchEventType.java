package com.fsd.dispatch.event;

public final class DispatchEventType {

    public static final String TASK_CREATED = "dispatch.task.created";
    public static final String TASK_ASSIGNED = "dispatch.task.assigned";
    public static final String TASK_MANUAL_ASSIGNED = "dispatch.task.manual-assigned";
    public static final String TASK_MANUAL_PENDING = "dispatch.task.manual-pending";
    public static final String TASK_EXECUTING = "dispatch.task.executing";
    public static final String TASK_SUCCESS = "dispatch.task.success";
    public static final String TASK_FAILED = "dispatch.task.failed";
    public static final String EXCEPTION_OPEN = "dispatch.exception.open";
    public static final String EXCEPTION_RESOLVED = "dispatch.exception.resolved";

    private DispatchEventType() {
    }
}

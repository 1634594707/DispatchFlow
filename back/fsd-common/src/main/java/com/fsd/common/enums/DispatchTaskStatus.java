package com.fsd.common.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 调度任务状态迁移表（唯一事实来源，路线图 2.2）。
 *
 * <p>所有状态流转必须经由本表校验；操作与允许前置状态的对应关系：</p>
 * <ul>
 *   <li>自动派车 AUTO_ASSIGN：PENDING / MANUAL_PENDING / ASSIGNING（卡死重入）→ ASSIGNING</li>
 *   <li>手动派车 MANUAL_ASSIGN：PENDING / MANUAL_PENDING → ASSIGNED</li>
 *   <li>批量派车 BATCH_ASSIGN：逐条复用自动/手动派车前置状态</li>
 *   <li>取消 CANCEL：PENDING / MANUAL_PENDING / ASSIGNING / ASSIGNED / EXECUTING → CANCELLED</li>
 *   <li>失败重试 RETRY：自动派单失败落 MANUAL_PENDING 后重走自动/手动派车；
 *       FAILED 仅能由 EXECUTING 进入且为终态，执行失败的重新调度必须另建任务</li>
 *   <li>车辆回报 VEHICLE_REPORT：START_EXECUTE ASSIGNED→EXECUTING；
 *       FINISH ASSIGNED/EXECUTING→SUCCESS；REPORT_FAIL EXECUTING→FAILED</li>
 *   <li>人工接管 UNASSIGN：ASSIGNED → PENDING（释放车辆后重新排队）；REASSIGN：ASSIGNED → ASSIGNED（换车自环）</li>
 * </ul>
 */
public enum DispatchTaskStatus {
    PENDING,
    ASSIGNING,
    ASSIGNED,
    EXECUTING,
    SUCCESS,
    FAILED,
    CANCELLED,
    MANUAL_PENDING;

    /** 允许的目标状态集合（终态为空集）。 */
    private static final Map<DispatchTaskStatus, Set<DispatchTaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            PENDING, EnumSet.of(ASSIGNING, MANUAL_PENDING, CANCELLED),
            MANUAL_PENDING, EnumSet.of(ASSIGNING, MANUAL_PENDING, CANCELLED),
            ASSIGNING, EnumSet.of(ASSIGNED, MANUAL_PENDING, PENDING, CANCELLED),
            // ASSIGNED -> PENDING：人工接管退回重新排队；ASSIGNED -> ASSIGNED：改派换车自环
            ASSIGNED, EnumSet.of(EXECUTING, SUCCESS, CANCELLED, PENDING, ASSIGNED),
            EXECUTING, EnumSet.of(SUCCESS, FAILED, CANCELLED),
            SUCCESS, EnumSet.noneOf(DispatchTaskStatus.class),
            FAILED, EnumSet.noneOf(DispatchTaskStatus.class),
            CANCELLED, EnumSet.noneOf(DispatchTaskStatus.class)
    );

    /** 当前状态是否允许流转到目标状态。 */
    public boolean canTransitionTo(DispatchTaskStatus target) {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(DispatchTaskStatus.class)).contains(target);
    }

    /** 当前状态允许的全部目标状态（只读视图）。 */
    public Set<DispatchTaskStatus> allowedNextStatuses() {
        return ALLOWED_TRANSITIONS.getOrDefault(this, EnumSet.noneOf(DispatchTaskStatus.class));
    }
}

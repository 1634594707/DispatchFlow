package com.fsd.dispatch.service;

import com.fsd.dispatch.core.DecisionTrace;
import com.fsd.dispatch.dispatch.DispatchAssignResult;
import com.fsd.dispatch.entity.DispatchDecisionSnapshotEntity;
import com.fsd.order.entity.OrderEntity;
import java.util.List;

public interface DispatchDecisionSnapshotService {

    /**
     * 写一条选车决策快照。实现必须保证：快照写失败不得影响派单本身。
     *
     * @param parkId         决策所属园区
     * @param trace          决策过程（候选漏斗与各分项分数）
     * @param result         决策结果，成功失败都要留痕
     * @param durationMicros 决策耗时（微秒）
     */
    void record(OrderEntity order, Long parkId, DecisionTrace trace,
                DispatchAssignResult result, long durationMicros);

    /**
     * 读侧（§7.3"任一历史任务可回答当时为什么选这台车"的另一半）：按任务取最近若干次决策。
     *
     * <p>一次任务可能先自动派单失败、再人工改派成功，所以返回**列表**而不是"最新一条"，
     * 顺序为决策时间倒序。
     */
    List<DispatchDecisionSnapshotEntity> latestByTask(Long taskId, int limit);

    /** 按订单取最近若干次决策，用途同上（订单尚未生成任务时仍可回答）。 */
    List<DispatchDecisionSnapshotEntity> latestByOrder(Long orderId, int limit);
}

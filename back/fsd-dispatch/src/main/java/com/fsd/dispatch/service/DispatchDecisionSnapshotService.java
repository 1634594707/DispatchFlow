package com.fsd.dispatch.service;

import com.fsd.dispatch.core.DecisionTrace;
import com.fsd.dispatch.dispatch.DispatchAssignResult;
import com.fsd.order.entity.OrderEntity;

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
}

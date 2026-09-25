package com.fsd.dispatch.dispatch;

import com.fsd.dispatch.core.RankedCandidate;
import com.fsd.order.entity.OrderEntity;
import java.util.List;

public interface DispatchVehicleAssignService {

    DispatchAssignResult selectBestVehicle(OrderEntity order);

    /**
     * 带撮合算法标识的入口（§2.3）：旧签名一字不动，批量入口在旁边加。
     *
     * @param matchAlgorithm {@code GREEDY} / {@code HUNGARIAN}，进决策快照用于事后回答
     *                       "这一单是逐单贪心得到的还是批量撮合得到的"
     */
    DispatchAssignResult selectBestVehicle(OrderEntity order, String matchAlgorithm);

    /**
     * 批量撮合的成本矩阵来源：跑完整候选漏斗与打分，但**不落 MAPF 预约、不写快照、不改任何状态**。
     *
     * <p>与 {@link #selectBestVehicle} 共用同一套判据是刻意的 —— 否则矩阵里"可行"的单在真正派车时
     * 会以另一套标准被判不可行，撮合收益就成了纸面数字。
     *
     * @return 按总分升序的可达候选；无候选时返回空列表
     */
    List<RankedCandidate> rankCandidatesForBatch(OrderEntity order);
}

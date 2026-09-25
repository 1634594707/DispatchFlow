package com.fsd.dispatch.dispatch;

import com.fsd.order.entity.OrderEntity;
import java.util.List;
import java.util.Map;

/**
 * 批量撮合入口（路线图 §2.3）。
 *
 * <p>与逐单贪心的区别不在"会不会派"，在<b>谁先占哪台车</b>：逐单贪心按到达顺序吃掉全局最优车，
 * 后面的单只能拿次优；批量入口对待派池一次性算成本矩阵，按矩阵给出的顺序派车。
 *
 * <p>刻意不改旧签名 {@link DispatchVehicleAssignService#selectBestVehicle(OrderEntity)}，也刻意
 * <b>不</b>在这里做任何可行性判定 —— 矩阵只决定顺序，真正的过滤、SOC 链校验、MAPF 预约仍由逐单链路做，
 * 避免出现"矩阵说可行、派单时另一套标准说不可行"的第二真相。
 */
public interface DispatchBatchAssignService {

    BatchAssignOutcome assignOrders(List<OrderEntity> pendingOrders);

    /**
     * @param algorithm        本 tick 实际使用的撮合算法（{@code HUNGARIAN} / {@code GREEDY}）
     * @param orders           进入本 tick 的单量
     * @param matched          矩阵给出配对的单量
     * @param greedyMatched    同一矩阵下贪心基线的配对单量（用来判断节省量是否可比）
     * @param totalScore       本 tick 配对总分（越小越好）
     * @param greedyTotalScore 贪心基线总分
     * @param savings          仅在两侧配对数相同时给出的节省量，否则为 null（不可比就不报数）
     * @param planInOrder      矩阵给出的派车顺序（订单 id → 计划车辆编码），顺序即提交顺序，
     *                         用于事后回答"为什么这单排在前面"；不保证最终派给它（见类注释的"只出顺序"）
     */
    record BatchAssignOutcome(String algorithm,
                              int orders,
                              int matched,
                              int greedyMatched,
                              double totalScore,
                              double greedyTotalScore,
                              Double savings,
                              Map<Long, String> planInOrder) {
    }
}

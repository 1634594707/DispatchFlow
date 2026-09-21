package com.fsd.dispatch.core;

import java.util.List;

/**
 * 决策策略插槽（§2.1）。
 *
 * <p>纯函数：入参是已经把外部世界解析完的快照，出参是排好序的候选清单。实现不得碰数据库、
 * Redis、Spring 上下文或系统时钟 —— 缺了这一层，任何新算法（学习模型、Jev、预测感知）
 * 都只能继续往派单热路径里塞 if 分支，而那段打分算式原本是写死在
 * {@code DispatchVehicleAssignServiceImpl} 里的。
 *
 * <p>三阶段晋级（SHADOW → GRAY → PRIMARY）由调用方负责，策略自己不知道自己是否在主链路上。
 */
public interface DecisionPolicy {

    /** 策略标识，进决策快照（如 {@code RULE} / {@code FORECAST} / {@code JEV}）。 */
    String id();

    /** 策略版本，与 {@link #id()} 一起构成可回滚的定位键。 */
    String version();

    DecisionOutcome decide(DecisionInput input);
}

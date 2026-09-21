package com.fsd.dispatch.core;

import java.util.List;

/**
 * 策略入参：一单决策所需的全部外部事实，由调用方在热路径里一次性解析好。
 *
 * <p>{@code peakMode} / {@code peakDistanceFactor} / {@code roadDistanceMetres} 都必须是已解析的值，
 * 策略实现内不得再去查配置或路网。
 */
public record DecisionInput(String orderPriority,
                            boolean peakMode,
                            double peakDistanceFactor,
                            DecisionWeights weights,
                            List<CandidateState> candidates) {

    /**
     * @param soc                  当前电量百分比；电量缺失时由调用方按满电传入（沿用修复前语义）
     * @param roadDistanceMetres   到取货点的路网距离（米，已含 geo 混合）
     * @param pluggedFullStandby   插电 + STANDBY + 满电，三者同时成立才给停车位奖励
     * @param idleMinutes          距最后一次上报的空闲分钟数，≤0 表示不奖励
     */
    public record CandidateState(RankedCandidateIdentity identity,
                                 int soc,
                                 double roadDistanceMetres,
                                 boolean pluggedFullStandby,
                                 long idleMinutes) {
    }

    /** 候选车的身份标识，避免策略层依赖 ORM 实体。 */
    public record RankedCandidateIdentity(Long vehicleId, String vehicleCode) {
    }
}

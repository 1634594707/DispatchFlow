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
     * @param forecastPressure     目标站点的预测压力（§2.1 实现 B），归一化 0..1，由调用方从
     *                             {@code t_energy_forecast} 解析好；规则策略与 SHADOW 未接线时恒为 0
     */
    public record CandidateState(RankedCandidateIdentity identity,
                                 int soc,
                                 double roadDistanceMetres,
                                 boolean pluggedFullStandby,
                                 long idleMinutes,
                                 double forecastPressure) {

        /** 无预测项的构造（§2.1 实现 A 的等价迁移、SHADOW 接线前的热路径都用这条），forecastPressure 恒为 0。 */
        public CandidateState(RankedCandidateIdentity identity,
                              int soc, double roadDistanceMetres,
                              boolean pluggedFullStandby, long idleMinutes) {
            this(identity, soc, roadDistanceMetres, pluggedFullStandby, idleMinutes, 0D);
        }
    }

    /** 候选车的身份标识，避免策略层依赖 ORM 实体。 */
    public record RankedCandidateIdentity(Long vehicleId, String vehicleCode) {
    }
}

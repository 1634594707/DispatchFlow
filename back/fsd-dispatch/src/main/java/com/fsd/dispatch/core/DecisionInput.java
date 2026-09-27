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
     * @param gravity              引力/运力压力视图（P0-3）；缺省 {@link GravityView#NONE}，
     *                             引力类策略据此计算，其余策略不消费
     */
    public record CandidateState(RankedCandidateIdentity identity,
                                 int soc,
                                 double roadDistanceMetres,
                                 boolean pluggedFullStandby,
                                 long idleMinutes,
                                 double forecastPressure,
                                 GravityView gravity) {

        /** 无预测项的构造（§2.1 实现 A 的等价迁移、SHADOW 接线前的热路径都用这条），forecastPressure 恒为 0。 */
        public CandidateState(RankedCandidateIdentity identity,
                              int soc, double roadDistanceMetres,
                              boolean pluggedFullStandby, long idleMinutes) {
            this(identity, soc, roadDistanceMetres, pluggedFullStandby, idleMinutes, 0D);
        }

        /** 压力项构造（FORECAST 路径），gravity 缺省 NONE。 */
        public CandidateState(RankedCandidateIdentity identity,
                              int soc, double roadDistanceMetres,
                              boolean pluggedFullStandby, long idleMinutes, double forecastPressure) {
            this(identity, soc, roadDistanceMetres, pluggedFullStandby, idleMinutes, forecastPressure, GravityView.NONE);
        }
    }

    /**
     * 引力/运力压力视图（P0-3）：候选车所属站区的在窗需求与空闲供给 + 本单卸货站区的在窗需求。
     * 三项全 0（{@link #NONE}）时引力项恒 0，{@code GravityPolicy} 逐位退回 RULE —— 构造保证的失败回退，
     * 预测/计数缺失在调用方就退化为 NONE，策略层不需要 try/catch。
     */
    public record GravityView(double homeDemand, double homeSupply, double destinationDemand) {

        public static final GravityView NONE = new GravityView(0D, 0D, 0D);

        public boolean isNone() {
            return homeDemand == 0D && homeSupply == 0D && destinationDemand == 0D;
        }
    }

    /** 候选车的身份标识，避免策略层依赖 ORM 实体。 */
    public record RankedCandidateIdentity(Long vehicleId, String vehicleCode) {
    }
}

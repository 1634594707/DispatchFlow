package com.fsd.dispatch.core;

/**
 * 打分权重（§7.1 决策内核的"成本函数"入参）。
 *
 * <p>刻意做成不可变值对象而不是直接读 {@code @ConfigurationProperties} bean：档案表覆盖、YAML 默认、
 * 灰度解析都在外层完成，内核只吃一份确定的权重，这样离线回放与影子对照才可复现。
 *
 * @param priorityHighFactor      高优先级订单的总分系数（越小越优先被选中）
 * @param priorityLowFactor       低优先级订单的总分系数
 * @param peakSocDamping          高峰时段对 SOC 惩罚的阻尼系数
 * @param pluggedBonusFalloffMetres 停车位奖励的距离衰减长度
 */
public record DecisionWeights(double weightDistance,
                              double weightSocMargin,
                              double weightPluggedStandbyBonus,
                              double weightFairness,
                              double maxIdleBonus,
                              int fullSoc,
                              double priorityHighFactor,
                              double priorityLowFactor,
                              double peakSocDamping,
                              double pluggedBonusFalloffMetres) {

    public static final double DEFAULT_PRIORITY_HIGH_FACTOR = 0.7D;
    public static final double DEFAULT_PRIORITY_LOW_FACTOR = 1.3D;
    public static final double DEFAULT_PEAK_SOC_DAMPING = 0.7D;
    public static final double DEFAULT_PLUGGED_BONUS_FALLOFF_METRES = 500D;
}

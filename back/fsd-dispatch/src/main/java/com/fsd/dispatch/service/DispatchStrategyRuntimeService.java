package com.fsd.dispatch.service;

import com.fsd.dispatch.config.DispatchScoringProperties;
import com.fsd.dispatch.config.FleetEnergyProperties;

public interface DispatchStrategyRuntimeService {

    /**
     * 解析一单派单应使用的策略（能量 + 打分 + 命中档案）。
     *
     * <p>灰度判定只在这里发生一次：同一 {@code bucketKey} 永远命中同一侧，因此调用方必须
     * 每个业务单元（一单 / 一辆车）解析一次并把结果向下传递，而不是分别取 energy 和 scoring
     * ——分别取会让同一单混用「实验侧阈值 + 生产侧权重」。
     *
     * @param bucketKey 稳定分桶键（如 {@code order:123}、{@code vehicle:ZJF-AV-07}）
     */
    AssignStrategy strategyForAssign(Long parkId, String bucketKey);

    void refreshCache();

    /**
     * @param productionSide true 表示落在对照（生产）侧，false 表示命中 EXPERIMENT 灰度桶
     */
    record AssignStrategy(FleetEnergyProperties energy,
                          DispatchScoringProperties scoring,
                          Long profileId,
                          String profileType,
                          Integer grayPercent,
                          int bucket,
                          boolean productionSide) {
    }
}

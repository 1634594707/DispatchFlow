package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 车队能量与补能判据配置（运行时仿真的唯一活路径）。
 *
 * <h2>口径纪律：对外物理值 ≠ 运行时仿真值（T1-c 定案，本注释即终审依据）</h2>
 *
 * <p>对外口径（讲给客户/简历用）：一次充电约 <b>2 h</b>、防爆换电柜快速换电约 <b>30 s</b>。
 * 这两个数来自 §0.1 [业务] 的新石器 L4 车辆事实，只在展示文案和 seed 的
 * {@code t_station.avg_service_seconds='30'} 里出现。
 *
 * <p>运行时节奏（演示用）：由 {@link #chargeRatePerTick}（+4 %/tick ⇒ 20→90 约 17.5 s）
 * 与 {@link #swapDurationTicks}（5 tick ≈ 5 s）决定，<b>不是物理值</b>。
 * 二者不一致是<b>有意设计</b>，不是缺陷，不要再"修正"成物理值：
 * 按 2 h 真跑，演示全程车会卡在充电。
 *
 * <p>已知不消费方：{@code avg_service_seconds} 在 back/ 下零消费（只随 seed 落库供展示），
 * 7,200 s 只存在于离线的 {@code ScenarioBench}，都属预期。改这些注释前请先复核该结论。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsd.fleet.energy")
public class FleetEnergyProperties {

    /** 地图/快照低电量展示阈值（%）。T1-b 与回补线取齐，避免出现"显示正常但在回补"的中间带。 */
    private int lowSocThreshold = 30;

    /**
     * 自动回充触发阈值（%）。SOC 低于/等于本值的 IDLE 车辆自动进入充电队列。
     * T1-b（Q1 裁"对齐 RMS"）：20 → 30，取自 §0.1 [业务] "RMS：低于 30% 自动回母港补能"。
     * 历史值 15%、20% 均低于 minAssignableSoc(30)，使 20~30% 区间的车既不可派也不回补。
     */
    private int returnToChargeThreshold = 30;

    /** 危急电量驻车阈值（%） */
    private int criticalSocThreshold = 5;

    /**
     * 最低可派单 SOC（%）。统一阈值：SOC 低于 30% 的车辆不再派新任务。
     * 历史值 25% 与 returnToChargeThreshold(15%) 之间只有 10% 余量，
     * 在长路径任务中容易耗尽电量；提升至 30% 给 10% 安全垫。
     */
    private int minAssignableSoc = 30;

    private int fullSoc = 100;

    /** 充电结束并恢复派单阈值（%） */
    private int chargeCompleteSoc = 90;

    private int chargeRatePerTick = 4;

    private int reserveSocFloor = 5;

    private int busyDrainIntervalTicks = 8;

    /**
     * 真实地图配送：每下降 1% SOC 约需行驶的米数（按 geo 弧长计）。
     *
     * 1,800 m/1% = 满电 180 km，取自现场车型规格（新石器 L4 满载续航约 180 km、标称 200 km，一次充电约 2 h）。
     * 旧默认 150 m/1%（满电 15 km）无出处，把车队续航压了一个数量级，曾直接产出
     * "26 台桩上限""6 桩被 20 台压爆""川姜跑不到"三条错结论 —— 不要调回小值。
     */
    private double busyDrainMetersPerPercent = 1800D;

    private double idleDrainProbability = 0.06D;

    private boolean pluggedStandbyNoDrain = true;

    private boolean idleChargeWhenNoDemand = true;

    /** CHARGE, SWAP, or AUTO */
    private String energyRecoveryMode = "CHARGE";

    private int swapDurationTicks = 5;

    /**
     * ALG-10 fix: maximum duration (minutes) a charging session may remain ACTIVE before
     * the timeout scheduler forcibly terminates it. A faulty charging pile that never
     * reports full SOC would otherwise leave the vehicle permanently stuck in CHARGING.
     * Set to 0 to disable the timeout (not recommended for production).
     */
    private int chargingTimeoutMinutes = 240;
}

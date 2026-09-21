package com.fsd.dispatch.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 补能需求预测接入配置（ALG-FC）。
 *
 * <p>默认开启读取，但预测表为空时所有判断都返回"无压力"，行为与接入前一致——
 * 即缺少模型数据不会改变生产派单/补能语义。
 */
@Data
@Component
@ConfigurationProperties(prefix = "fsd.energy-forecast")
public class EnergyForecastProperties {

    /** 预测读取总开关。 */
    private boolean enabled = true;

    /** 是否允许在高压力时段推迟返充（错峰补能）。 */
    private boolean deferReturnEnabled = true;

    /**
     * 站点/园区压力阈值：pressureP95 达到该值视为高峰。
     *
     * <p>单位是"该站该小时开始的充电会话数"（见 {@code scripts/ml/export_energy_features.sql}）。
     * 真实规模下 2.0 合理；但在压测夹具生成的特征上（实测小时均值 150.1、峰谷比 1.08）这个绝对
     * 阈值**恒成立**，所以还必须同时过 {@link #minPeakPressureRatio}，见该字段的说明。
     */
    private double pressureThreshold = 2.0D;

    /**
     * 高峰的**相对**判据：当前小时压力至少要达到当日"逐小时峰值中位数"的多少倍才算高峰。
     *
     * <p>存在的理由是一个可复现的危险：把绝对阈值单独用作门控时，阈值 2.0 对任何
     * "小时到达数"量级的剖面都恒成立（实测 2026-09-17 那份剖面：152.7~159.85，
     * 阈值 ≤152.7 时 24/24 小时全触发），于是日作业一旦被调度起来，错峰返充就从"无操作"
     * 翻转为"整天推迟"，而它唯一的兜底就剩 SOC 临界阈值。相对判据让"剖面太平"退化成
     * 不推迟（安全侧），而不是退化成一整天不返充。
     *
     * <p>取 1.5 而不是更高：这是"峰段与平段可分"的经验下限，且与 M3 里人工设定的
     * {@code ArrivalProfile} 矩形峰（2.0 倍）相容。设 ≤0 即关闭该判据，退回纯绝对阈值。
     */
    private double minPeakPressureRatio = 1.5D;

    /** 推迟返充所需的最小 SOC 余量（相对 criticalSocThreshold）；余量不足则安全优先、立即返充。 */
    private int deferMarginSoc = 10;

    /** 预测数据最大有效期（小时）；超过则忽略该预测。 */
    private int maxDataAgeHours = 24;
}

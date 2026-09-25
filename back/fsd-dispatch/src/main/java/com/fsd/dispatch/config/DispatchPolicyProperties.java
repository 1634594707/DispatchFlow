package com.fsd.dispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 决策策略晋级开关（§2.2）。
 *
 * <p>三阶段的语义差别不是"用不用新策略"，而是**新策略有没有资格改变线上结果**：
 * <ul>
 *   <li>{@code OFF}：挑战者策略完全不参与，连计算都不做（默认值，行为与本开关引入前一致）；</li>
 *   <li>{@code SHADOW}：在位策略决定结果，挑战者对同一份入参再算一遍，只记录一致率与 regret；</li>
 *   <li>{@code GRAY}：按稳定分桶把一部分单交给挑战者，其余仍在位策略；</li>
 *   <li>{@code PRIMARY}：挑战者接管，只允许在 SHADOW + GRAY 数字达标后设置。</li>
 * </ul>
 *
 * <p>{@code mode} 与 {@code grayPercent} 可经管理端运行时覆盖（{@code DecisionPolicyRouter} 持有 volatile 覆盖值），
 * §2.2 要求"回退开关必须一分钟内可切"，而重启后端远超这个时间。
 */
@Component
@ConfigurationProperties(prefix = "fsd.dispatch.policy")
public class DispatchPolicyProperties {

    /** 晋级阶段，见类注释。 */
    public enum Mode {
        OFF, SHADOW, GRAY, PRIMARY
    }

    private Mode mode = Mode.OFF;

    /** 挑战者策略标识，取自 {@code DecisionPolicy.id()}（如 {@code FORECAST}）。 */
    private String challenger = "FORECAST";

    /** GRAY 阶段交给挑战者的桶占比 0-100，分桶键为订单稳定标识。 */
    private int grayPercent = 0;

    /** 批量撮合入口总开关（§2.3）：关闭时待派池仍逐单走贪心链路。 */
    private boolean batchEnabled = false;

    /** 批量撮合算法：{@code HUNGARIAN} 或 {@code GREEDY}（后者是 fallback，求解器异常时也自动降级到它）。 */
    private String batchAlgorithm = "HUNGARIAN";

    /** 单次撮合最多处理多少单，防止待派池积压时一次 tick 把热路径时延顶起来。 */
    private int batchMaxPoolSize = 20;

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.OFF : mode;
    }

    public String getChallenger() {
        return challenger;
    }

    public void setChallenger(String challenger) {
        this.challenger = challenger == null || challenger.isBlank() ? "FORECAST" : challenger.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public int getGrayPercent() {
        return grayPercent;
    }

    public void setGrayPercent(int grayPercent) {
        this.grayPercent = Math.max(0, Math.min(100, grayPercent));
    }

    public boolean isBatchEnabled() {
        return batchEnabled;
    }

    public void setBatchEnabled(boolean batchEnabled) {
        this.batchEnabled = batchEnabled;
    }

    public String getBatchAlgorithm() {
        return batchAlgorithm;
    }

    public void setBatchAlgorithm(String batchAlgorithm) {
        this.batchAlgorithm = batchAlgorithm == null || batchAlgorithm.isBlank()
                ? "HUNGARIAN" : batchAlgorithm.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public int getBatchMaxPoolSize() {
        return batchMaxPoolSize;
    }

    public void setBatchMaxPoolSize(int batchMaxPoolSize) {
        this.batchMaxPoolSize = Math.max(1, batchMaxPoolSize);
    }
}

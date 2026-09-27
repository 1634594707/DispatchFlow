package com.fsd.dispatch.sim;

import com.fsd.dispatch.core.ForecastAwarePolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * P0-1 离线实验入口（面试路线图）：同一份订单流（同种子同重复次数）下比较在位 RULE、
 * 批量撮合（匈牙利）与 FORECAST（预测感知），并做 grayPercent 分侧扫描；
 * 每张表同时有收益与代价指标，判语只在配对差 95% CI 不跨 0 时给方向。
 *
 * <p>纯 JVM（无 DB/Redis/RabbitMQ/Spring），产物是算法相对比较证据，<b>不是线上承载证据</b>。
 * 用法（仓库根目录）：
 * <pre>
 *   mvn -pl fsd-dispatch -am compile -DskipTests -q
 *   java -cp back/fsd-dispatch/target/classes com.fsd.dispatch.sim.DispatchPolicyExperiment [输出路径.md]
 * </pre>
 * 系统属性：{@code bench.seed}（默认 20260921）、{@code bench.repeats}（默认 40）。
 */
public final class DispatchPolicyExperiment {

    private DispatchPolicyExperiment() {
    }

    public static void main(String[] args) throws java.io.IOException {
        long seed = Long.getLong("bench.seed", 20260921L);
        int repeats = Integer.getInteger("bench.repeats", 40);
        ScenarioBench.Config base = ScenarioBench.Config.mTier(seed)
                .withStationDemand(1.0D)
                .withPeakDemand()
                .withRepeats(repeats);
        ForecastAwarePolicy mild = new ForecastAwarePolicy(ForecastAwarePolicy.DEFAULT_PRESSURE_WEIGHT);
        ForecastAwarePolicy strong = new ForecastAwarePolicy(300D);
        com.fsd.dispatch.core.GravityPolicy gravity = new com.fsd.dispatch.core.GravityPolicy();
        com.fsd.dispatch.core.GravityPolicy gravityStrong = new com.fsd.dispatch.core.GravityPolicy(300D, 50D);

        StringBuilder md = new StringBuilder();
        md.append("# 调度策略 A/B 实验报告（2026-09）\n\n");
        md.append("> P0-1（面试就绪路线图）产物：同一订单流下比较策略；配对差 = 逐次同种子相减。\n")
                .append("> 产物生成入口：`com.fsd.dispatch.sim.DispatchPolicyExperiment`，一条命令可复现。\n\n");
        md.append(environmentSection(seed, repeats));

        ScenarioBench.Config batch = base.withMatchStrategy(ScenarioBench.MatchStrategy.HUNGARIAN);
        md.append("---\n\n");
        md.append(ScenarioBench.compareReport(base, batch,
                "实验一：批量撮合（匈牙利）vs 现状逐单贪心（M 档，站点需求 + 高峰）",
                "现状：逐单贪心", "批量撮合：匈牙利",
                ScenarioBench.comparePaired(base, batch)));

        md.append("---\n\n");
        md.append(ScenarioBench.compareReport(base, base,
                "实验二：FORECAST@100 vs RULE（PRIMARY 全量接管）",
                "RULE（在位）", "FORECAST@100（挑战者）",
                ScenarioBench.comparePoliciesPaired(base, mild)));

        md.append("---\n\n");
        md.append(ScenarioBench.compareReport(base, base,
                "实验三：FORECAST@300 vs RULE（PRIMARY 全量接管）",
                "RULE（在位）", "FORECAST@300（挑战者）",
                ScenarioBench.comparePoliciesPaired(base, strong)));

        ScenarioBench.Config windowed = base.withMatchWindow(4);
        md.append("---\n\n");
        md.append(ScenarioBench.compareReport(windowed, windowed,
                "实验四：FORECAST@300 vs RULE（攒 4 tick 窗口，等待可观测形态）",
                "RULE（在位）", "FORECAST@300（挑战者）",
                ScenarioBench.comparePoliciesPaired(windowed, strong)));

        md.append("---\n\n");
        md.append(ScenarioBench.compareReport(base, base,
                "实验五：GRAVITY（引力/运力压力，默认权重）vs RULE（PRIMARY 全量接管）",
                "RULE（在位）", "GRAVITY（挑战者）",
                ScenarioBench.comparePoliciesPaired(base, gravity)));

        md.append("---\n\n");
        md.append(ScenarioBench.compareReport(batch, batch,
                "实验六：GRAVITY@强拉离 × 批量撮合 vs RULE × 批量撮合（引力项进成本矩阵）",
                "RULE × 匈牙利", "GRAVITY(300,50) × 匈牙利",
                ScenarioBench.comparePoliciesPaired(batch, gravityStrong)));
        md.append("""
                > **引力策略的三分钟讲法**：拉离惩罚 = `需求 × 优先级权重 / max(供给, 1)`——把一台车从
                > "在窗需求高、空闲供给少"的站区拉走是这笔派单的绝对成本，不乘优先级系数；目的地引力 =
                > `需求 / (距离km + ε)²`——车送完落在哪很重要，高需求站区给抵扣。硬约束（车型/载重/SOC
                > 全链路可达/路网可达）在候选构建时已过滤，引力分数碰不到不可达车；预测或计数缺失时
                > 引力视图退化为全 0，策略逐位退回 RULE——回退是构造保证的，不靠 try/catch。
                > 逐单贪心下目的地需求对同一单是常数，只有进批量撮合的成本矩阵它才携带跨单信号（实验六）。

                """);

        md.append("---\n\n");
        md.append(ScenarioBench.grayScanReport(base, strong, ScenarioBench.grayScan(base, strong, 10, 25, 50)));

        md.append("---\n\n## 假设声明（assumptions() 逐字）\n\n");
        for (String s : ScenarioBench.assumptions()) {
            md.append("- ").append(s).append('\n');
        }

        md.append("""
                \n---\n## 诚实边界

                - 需求是合成的：站点坐标、skew 与高峰档都是情景设定（见假设声明），结论只读作**同一参数集下策略间的相对排序**，不代表生产绝对精度。
                - 压力项是取货站的**在线计数代理**，不是 `t_energy_forecast`；接真预测表的对照要等 P0-2 数据质量门禁收紧后再做。
                - 事后下界（regret vs hindsight）偏乐观：模型在接单瞬间把车挪到卸货点（假设声明有注记）。
                - 单一场景、单一种子族、旧图口径：换场景或换现行 seed 头部（18.73 km/h / 1.416）必须整体重跑，不能只改一臂、不能跨口径外推。
                - 本报告无 DB/Redis 参与，不构成规模或线上稳定性结论（那挂 P1-3 的真实基础设施压测）。
                """);

        Path out = Path.of(args.length > 0 ? args[0] : "reports/experiments/dispatch-policy-2026-09.md");
        Path parent = out.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(out, md.toString(), StandardCharsets.UTF_8);
        System.out.println("[OK] 报告已写 " + out.toAbsolutePath());
    }

    private static String environmentSection(long seed, int repeats) {
        return """
                ## 环境与口径（先读这段再读数字）

                - **纯 JVM 仿真**（`sim.ScenarioBench`）：无 DB / Redis / RabbitMQ / Spring 参与。这是算法相对比较证据，**不是线上承载证据**（规模证据挂 P1-3）。
                - 路网口径：**旧图** `osm-expanded-2026-09-21/91n-113e-24354m`；M 档常数 avgSpeed 17.84 km/h、detour 1.481 与它同源。现行 seed 头部（东界扩围后）是 18.73 km/h / 1.416，引用本报告数字必须带"旧图口径"限定。
                - 场景：M 档 20 车 / 56 单每小时 / 120 分钟 / 6 桩单点 / OPPORTUNISTIC 补能，TICK=15 s；需求 = ZJF 站点 skew=1.0 + 第 2 小时高峰 ×2。
                - 种子 BENCH_SEED，重复 n=BENCH_REPEATS；同一串种子跨臂共享 → 配对差，均值差异必须过"95% CI 不跨 0"才允许说方向。
                - 决策内核与生产同一份 `core.RulePolicy`（纯函数、无 Spring）；FORECAST = RULE + 站点压力×权重。压力是**每台候选各自**的：取离它最近的取货站（它此刻所属站区）在 pressureWindowTicks 窗口内的落单数 / pressureDeferOrders（封顶 1），对应生产『车辆目标/所属站点的预测压力』——是**在线计数代理，不是 t_energy_forecast**。
                - GRAVITY（P0-3）= RULE + 拉离惩罚（`需求×w/max(供给,1)`，供给 = 本 tick 候选集内同站区空闲车数，含自身）− 目的地引力（`需求/(距离km+ε)²`，ε=0.05 km）。引力事实（GravityView）全 0 时逐位退回 RULE；目的地需求在逐单贪心下不改排序，进 Hungarian 成本矩阵才携带跨单信号（实验六）。
                - 灰度分桶：生产 `DecisionPolicyRouter.bucket(单 id)` 逐字同源；挑战者抛错自动回落在位并计数（进灰度告警）。
                - 指标定义：完成率 = 完成/到达；等待 = 派单时刻 − 到达时刻（端到端完成时长含等待；窗口=1 时来单即派，等待恒 0，等待的形态见实验四）；空驶率 = 1 − 载货段/总里程；低 SOC 拒单率 = LOW_SOC 次数/到达（SOC 硬约束使"跑到一半没电"不可观测，这是可观测的替代信号）；decision regret = 挑战者选择按在位标尺的分差（同生产 Router 口径）；side_service_rate = 侧内 served/(served+配对丢失)。

                """.replace("BENCH_SEED", Long.toString(seed)).replace("BENCH_REPEATS", Integer.toString(repeats));
    }
}

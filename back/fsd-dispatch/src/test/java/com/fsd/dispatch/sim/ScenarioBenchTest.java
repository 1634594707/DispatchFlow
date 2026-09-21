package com.fsd.dispatch.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.sim.ScenarioBench.Config;
import com.fsd.dispatch.sim.ScenarioBench.DemandCell;
import com.fsd.dispatch.sim.ScenarioBench.PairedSummary;
import com.fsd.dispatch.sim.ScenarioBench.RunResult;
import com.fsd.dispatch.sim.ScenarioBench.Summary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §5 / M3 验收：固定种子可复现、N 次重复带置信区间、报告落盘。
 *
 * <p>这条测试同时是 CI 里的"基准没漂"守卫：任何改动让同一种子的结果变了，就会在这里红。
 */
class ScenarioBenchTest {

    @Test
    @DisplayName("同一配置同一种子，两次运行逐指标一致")
    void sameSeedIsFullyDeterministic() {
        Config cfg = Config.mTier(20260921L).withRepeats(3);

        RunResult a = ScenarioBench.run(cfg, cfg.seed() * 31);
        RunResult b = ScenarioBench.run(cfg, cfg.seed() * 31);

        assertEquals(a.completed(), b.completed());
        assertEquals(a.totalDistanceMeters(), b.totalDistanceMeters(), 0D);
        assertEquals(a.completionP95Seconds(), b.completionP95Seconds(), 0D);
        assertEquals(a.regretVsHindsight(), b.regretVsHindsight(), 0D);
        assertEquals(a.failureReasons(), b.failureReasons());
    }

    @Test
    @DisplayName("换种子结果必须变（否则说明随机性没接上）")
    void differentSeedChangesTheOutcome() {
        Config cfg = Config.mTier(11L).withRepeats(3);

        RunResult a = ScenarioBench.run(cfg, 1L);
        RunResult b = ScenarioBench.run(cfg, 2L);

        assertTrue(Math.abs(a.totalDistanceMeters() - b.totalDistanceMeters()) > 1D,
                "两次运行的里程完全相同，随机流未生效");
    }

    @Test
    @DisplayName("M 档跑满 N 次重复并产出带置信区间的指标表")
    void writesReportWithConfidenceIntervals() throws Exception {
        // N 可由 -Dbench.repeats 覆盖（scripts/dev/scenario-bench.sh 用它做快速冒烟）
        Config cfg = Config.mTier(20260921L).withRepeats(Integer.getInteger("bench.repeats", 12));

        List<Summary> summaries = ScenarioBench.summarise(cfg);

        assertEquals(17, summaries.size());
        for (Summary s : summaries) {
            assertTrue(s.n() == cfg.repeats(), s.metric() + " 样本数不是重复次数");
            assertTrue(s.high() >= s.mean() && s.mean() >= s.low(), s.metric() + " 置信区间不包均值");
        }
        Summary distance = summaries.stream().filter(s -> s.metric().equals("total_distance_m")).findFirst().orElseThrow();
        assertTrue(distance.high() > distance.low(),
                "里程的置信区间宽度为 0，说明 N 次重复其实在跑同一个场景");

        // 事后基线必须与"实际里程"同量纲：漏掉载货段这类错误会把 regret 顶到几倍，而不是百分之几十
        Summary regret = summaries.stream().filter(s -> s.metric().equals("regret_vs_hindsight")).findFirst().orElseThrow();
        assertTrue(regret.mean() > 0D && regret.mean() < 1D,
                "regret 超出合理区间，事后下界的口径很可能少算了一段: " + regret);

        String report = ScenarioBench.report(cfg, summaries);
        assertTrue(report.contains("档位 **M**"), "报告缺档位标签");
        assertTrue(report.contains("假设声明"), "报告缺假设声明");
        // 小样本必须用 t 分位数而不是 1.96。这里曾把错位表里的 2.179 当期望值钉着 —— 见
        // studentTUsesNMinusOneDegreesOfFreedom：n=12 的正确档是 t(df=11)=2.201。
        assertEquals(2.201D, ScenarioBench.studentT(12), 1e-9);
        assertEquals(1.96D, ScenarioBench.studentT(40), 1e-9);

        Path dir = Path.of("..", "..", "reports", "scenario-bench").normalize();
        Files.createDirectories(dir);
        Path file = dir.resolve("m-tier-bench.md");
        Files.writeString(file, report, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        System.out.printf(Locale.ROOT, "[scenario-bench] 报告写出 %s%n", file.toAbsolutePath());
        summaries.forEach(s -> System.out.println("  " + s));
    }

    @Test
    @DisplayName("空驶率/失败分布/充电次数都是活指标（不是恒 0 的假数字）")
    void metricsAreActuallyPopulated() {
        Config cfg = Config.mTier(7L).withRepeats(4);

        List<RunResult> runs = new java.util.ArrayList<>();
        for (int i = 0; i < cfg.repeats(); i++) {
            runs.add(ScenarioBench.run(cfg, cfg.seed() * 31 + i));
        }
        double avgDeadhead = runs.stream().mapToDouble(RunResult::deadheadShare).average().orElseThrow();
        int completed = runs.stream().mapToInt(RunResult::completed).sum();
        int charges = runs.stream().mapToInt(RunResult::chargeSessions).sum();

        assertTrue(completed > 0, "一单都没完成，场景或模型不通");
        assertTrue(avgDeadhead > 0.05 && avgDeadhead < 0.95,
                "空驶率超出可信区间: " + avgDeadhead);
        assertTrue(runs.stream().anyMatch(r -> !r.failureReasons().isEmpty()),
                "失败原因分布恒空，说明压力没打满车队");
        // 充电口径曾经写错（等待 0 tick 就不复位 SOC），所以这条必须钉住
        assertTrue(charges > 0, "两小时跑完一次都没充电，说明 SOC 复位链路没接上");
        // M 档 6 根桩在"空闲即补能"下确实排队：这条是 §1.3"桩数不扩"结论的反证，别让它悄悄变 0
        assertTrue(runs.stream().mapToDouble(RunResult::chargeBlockedCarMinutes).average().orElseThrow() > 0D,
                "补能从未被桩位挡住，说明 §1.3 的 6 桩上限没被打到，档位压力不够");
    }

    @Test
    @DisplayName("补能时机三臂对照：「补不补」给上界、「何时补」才是 M4 要的那个数（只有配对 CI 不跨 0 才写方向）")
    void chargeTimingArmsAreComparedWithPairedConfidence() throws Exception {
        Config base = Config.mTier(20260921L).withRepeats(12);
        Config opportunistic = base.withChargeTiming(ScenarioBench.ChargeTiming.OPPORTUNISTIC);
        Config never = base.withChargeTiming(ScenarioBench.ChargeTiming.NEVER);
        Config defer = base.withChargeTiming(ScenarioBench.ChargeTiming.DEFER_UNDER_PRESSURE);

        List<PairedSummary> onOff = ScenarioBench.comparePaired(opportunistic, never);
        List<PairedSummary> peakShift = ScenarioBench.comparePaired(defer, opportunistic);

        // 敏感性：窗口拉到整段仿真 + 阈值 1，等于"只要历史上落过单就再也不顺势补"。
        // 用来排除"错峰没收益是因为分支几乎没触发"这一解释。
        Config aggressive = defer.withPressureWindow(base.horizonMinutes() * 60 / ScenarioBench.TICK_SECONDS, 1);
        List<PairedSummary> sensitivity = ScenarioBench.comparePaired(aggressive, opportunistic);

        assertEquals(13, onOff.size());
        assertEquals(13, peakShift.size());
        for (PairedSummary s : onOff) {
            assertTrue(s.high() >= s.diff() && s.diff() >= s.low(), s.metric() + " 差值不在自己的区间里");
            assertEquals(base.repeats(), s.n(), s.metric() + " 配对样本数不等于重复次数");
            if (!s.distinguishable()) {
                assertTrue(s.low() <= 0D && s.high() >= 0D, s.metric() + " 判语与区间不自洽");
            }
        }
        // 「补不补」必须量得出差别，否则补能支路没接上（历史上真错过一次：等待 0 tick 就不复位 SOC）
        assertTrue(onOff.stream().anyMatch(PairedSummary::distinguishable),
                "补不补能都分不出来：开关没接进决策，或压力不够");
        // 第三臂必须真的改变行为；全 0 说明窗口/阈值没被触发过，等于没测
        assertTrue(peakShift.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "DEFER_UNDER_PRESSURE 与 OPPORTUNISTIC 逐指标完全相同 —— 推迟分支从未触发，检查 pressureWindowTicks/pressureDeferOrders");
        assertTrue(sensitivity.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "最凶的推迟条件都不改变任何指标 —— 补能时机支路是死的，先查策略有没有接进去");

        // 齐次到达下"高峰"根本不存在，所以 §13.12 的"错峰无收益"结论必须在有峰段的场景里重测一遍。
        Config peakBase = base.withPeakDemand();
        Config peakOn = peakBase.withChargeTiming(ScenarioBench.ChargeTiming.DEFER_IN_PEAK);
        Config peakOff = peakBase.withChargeTiming(ScenarioBench.ChargeTiming.OPPORTUNISTIC);
        List<PairedSummary> inPeak = ScenarioBench.comparePaired(peakOn, peakOff);
        assertEquals(13, inPeak.size());
        assertTrue(inPeak.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "有峰段场景里 DEFER_IN_PEAK 与现状完全相同 —— 峰段信号没接进去");

        // §13.14③ 那个 +7.38pp 产自"补能不开车过去"的模型。§13.16 补上这条腿后必须重算一遍：
        // 同一对臂、同种子、同场景，只加上"6 根桩挂在主站、车要开过去"这个物理事实。
        Config peakOnAtPile = peakOn.withChargeLayout(Config.ChargeLayouts.singlePointSixPiles(),
                Config.PileChoice.NEAREST_FREE);
        Config peakOffAtPile = peakOff.withChargeLayout(Config.ChargeLayouts.singlePointSixPiles(),
                Config.PileChoice.NEAREST_FREE);
        List<PairedSummary> inPeakWithLeg = ScenarioBench.comparePaired(peakOnAtPile, peakOffAtPile);
        assertEquals(13, inPeakWithLeg.size());

        String report = ScenarioBench.compareReport(opportunistic, never, "补能时机对照（M 档 · 齐次到达）",
                "`chargeTiming=OPPORTUNISTIC`（空闲即补能，现状 `idleChargeWhenNoDemand=true`）",
                "`chargeTiming=NEVER`（只在必充阈值以下才回桩）", onOff)
                + "\n---\n\n"
                + ScenarioBench.compareReport(defer, opportunistic, "错峰推迟 vs 现状（M 档，M4 要的那个数）",
                        "`chargeTiming=DEFER_UNDER_PRESSURE`（近 " + defer.pressureWindowTicks()
                                + " tick 落单 ≥ " + defer.pressureDeferOrders() + " 时推迟顺势补）",
                        "`chargeTiming=OPPORTUNISTIC`（现状）", peakShift)
                + "\n---\n\n"
                + ScenarioBench.compareReport(peakOn, peakOff,
                "真·错峰：高峰时段推迟返充 vs 现状（M 档 · **有峰段场景**，第 2 小时强度 ×2）",
                "`DEFER_IN_PEAK`（高峰段不顺势补，平段照常；必充档不受影响）",
                "`OPPORTUNISTIC`（现状）", inPeak)
                + "\n---\n\n"
                + ScenarioBench.compareReport(aggressive, opportunistic, "敏感性检查：把推迟条件调到最凶（M 档）",
                        "窗口 = 整段仿真、阈值 = 1（本单之后只要落过一单就再也不顺势补）",
                        "`chargeTiming=OPPORTUNISTIC`（现状）", sensitivity)
                + "\n---\n\n"
                + ScenarioBench.compareReport(peakOnAtPile, peakOffAtPile,
                        "⑤ 同一个真·错峰对照，但**补能要开车过去**（现役布局：6 桩挂在 ZJF-CHG-01）"
                                + " —— §13.14③ 的修正数，替代那张表里的 completion_rate/接驾距离",
                        "`DEFER_IN_PEAK` + 开去充电", "`OPPORTUNISTIC` + 开去充电", inPeakWithLeg);

        Path file = Path.of("..", "..", "reports", "scenario-bench", "charge-timing-m-tier.md").normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, report, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        System.out.printf(Locale.ROOT, "[scenario-bench] 对照报告写出 %s%n", file.toAbsolutePath());
        System.out.println("  —— 错峰推迟 vs 现状（差值正=推迟臂更高）");
        peakShift.forEach(s -> System.out.println("  " + s.metric() + " A=" + s.armA() + " B=" + s.armB()
                + " diff=" + s.diff() + " [" + s.low() + ", " + s.high() + "] "
                + (s.distinguishable() ? "可分" : "分不出来")));
        System.out.println("  —— 敏感性（最凶推迟）vs 现状");
        sensitivity.forEach(s -> System.out.println("  " + s.metric() + " diff=" + s.diff()
                + " [" + s.low() + ", " + s.high() + "] " + (s.distinguishable() ? "可分" : "分不出来")));
    }

    @Test
    @DisplayName("匈牙利解必须等于穷举最优（否则「最优臂」是假的，撮合收益数字全部作废）")
    void hungarianMatchesBruteForceOptimum() {
        java.util.Random rng = new java.util.Random(20260921L);
        for (int trial = 0; trial < 60; trial++) {
            int rows = 1 + rng.nextInt(4);
            int cols = rows + rng.nextInt(3);
            double[][] cost = new double[rows][cols];
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    cost[i][j] = rng.nextInt(100) / 10D;
                }
            }
            int[] assign = ScenarioBench.hungarian(cost);
            assertEquals(rows, assign.length);
            Set<Integer> distinct = new HashSet<>();
            for (int j : assign) {
                assertTrue(j >= 0 && j < cols, "行分到了不存在的列");
                assertTrue(distinct.add(j), "同一列被两行占用");
            }
            assertEquals(bruteForceMin(cost, rows, cols), total(cost, assign), 1e-9,
                    "匈牙利解不是最优：" + java.util.Arrays.deepToString(cost));
        }
    }

    private static double total(double[][] cost, int[] assign) {
        double t = 0D;
        for (int i = 0; i < assign.length; i++) {
            t += cost[i][assign[i]];
        }
        return t;
    }

    /** 枚举所有"每行占一个不同列"的注入映射取最小值。rows<=4、cols<=6，规模很小。 */
    private static double bruteForceMin(double[][] cost, int rows, int cols) {
        double[] best = {Double.MAX_VALUE};
        pick(cost, rows, cols, new int[rows], new boolean[cols], 0, best);
        return best[0];
    }

    private static void pick(double[][] cost, int rows, int cols, int[] assign, boolean[] usedCol, int row,
                             double[] best) {
        if (row == rows) {
            best[0] = Math.min(best[0], total(cost, assign));
            return;
        }
        for (int j = 0; j < cols; j++) {
            if (usedCol[j]) {
                continue;
            }
            usedCol[j] = true;
            assign[row] = j;
            pick(cost, rows, cols, assign, usedCol, row + 1, best);
            usedCol[j] = false;
        }
    }

    @Test
    @DisplayName("批量撮合 vs 逐单贪心：M 档配对差与判定（M5 撮合收益要的那个数）")
    void batchMatchingIsComparedAgainstGreedy() throws Exception {
        Config base = Config.mTier(20260921L).withRepeats(12);
        Config greedy = base.withMatchStrategy(ScenarioBench.MatchStrategy.SEQUENTIAL_GREEDY);
        Config batch = base.withMatchStrategy(ScenarioBench.MatchStrategy.HUNGARIAN);

        List<PairedSummary> rows = ScenarioBench.comparePaired(batch, greedy);

        assertEquals(13, rows.size());
        assertTrue(rows.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "批量撮合与贪心逐指标完全相同 —— matchStrategy 没接进去，等于没测");
        long distinguishable = rows.stream().filter(PairedSummary::distinguishable).count();

        // 关键结构事实：窗口=1 时同一 tick 平均只有 56/240 = 0.23 单，"没有可一起配的单"，
        // 所以配对算法再优也无从发挥。要量出撮合收益必须先有决策窗口。
        int windowTicks = 8;                                     // 2 分钟攒一批
        Config greedyW = greedy.withMatchWindow(windowTicks);
        Config batchW = batch.withMatchWindow(windowTicks);
        List<PairedSummary> pairingOnly = ScenarioBench.comparePaired(batchW, greedyW);   // 固定窗口，只看配对
        List<PairedSummary> totalEffect = ScenarioBench.comparePaired(batchW, greedy);    // 含窗口带来的等待
        assertEquals(13, pairingOnly.size());
        assertEquals(13, totalEffect.size());
        assertTrue(totalEffect.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "开了决策窗口却逐指标与即时派单相同 —— 窗口没生效，检查 matchWindowTicks");

        String report = ScenarioBench.compareReport(batch, greedy, "批量撮合 vs 逐单贪心（M 档，窗口=1 即无窗口）",
                "`matchStrategy=HUNGARIAN`（同 tick 全局最小代价配对）",
                "`matchStrategy=SEQUENTIAL_GREEDY`（先到先得，现状语义）", rows)
                + "\n> 窗口=1 时每 tick 平均到货 " + String.format(Locale.ROOT, "%.2f",
                base.ordersPerHour() / 3600D * ScenarioBench.TICK_SECONDS)
                + " 单，绝大多数 tick 只有一单甚至没有，**配对算法没有发挥空间** —— 下面的加窗口对照才是 M5 要问的。\n\n---\n\n"
                + ScenarioBench.compareReport(batchW, greedyW,
                "固定窗口 " + windowTicks + " tick，只比配对算法（M 档）",
                "`HUNGARIAN` + 窗口", "`SEQUENTIAL_GREEDY` + 窗口", pairingOnly)
                + "\n---\n\n"
                + ScenarioBench.compareReport(batchW, greedy,
                "批量撮合（含窗口代价）vs 现状即时贪心（M 档）",
                "`HUNGARIAN` + 窗口 " + windowTicks + " tick（2 分钟）",
                "现状：来一单派一单", totalEffect);
        Path file = Path.of("..", "..", "reports", "scenario-bench", "batch-matching-m-tier.md").normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, report, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        System.out.printf(Locale.ROOT, "[scenario-bench] 撮合对照写出 %s%n", file.toAbsolutePath());
        System.out.println("  —— 无窗口：可分指标 " + distinguishable + " / " + rows.size());
        System.out.println("  —— 同窗口只比配对：");
        pairingOnly.forEach(s -> System.out.println("  " + s.metric() + " diff=" + s.diff()
                + " [" + s.low() + ", " + s.high() + "] " + (s.distinguishable() ? "可分" : "分不出来")));
        System.out.println("  —— 加窗口后的总效应（vs 现状即时派单）：");
        totalEffect.forEach(s -> System.out.println("  " + s.metric() + " diff=" + s.diff()
                + " [" + s.low() + ", " + s.high() + "] " + (s.distinguishable() ? "可分" : "分不出来")));
    }

    @Test
    @DisplayName("M6 站点×小时需求画像：P90 >= P50 >= 0，且各站合计等于到达单数")
    void demandProfileIsConsistent() throws Exception {
        Config cfg = Config.mTier(20260921L).withStationDemand(0.9D);

        List<DemandCell> cells = ScenarioBench.demandProfile(cfg);

        assertEquals(2 * 2, cells.size(), "2 个取货站 × 2 小时");
        double sumOfMeans = cells.stream().mapToDouble(DemandCell::mean).sum();
        double expectedArrivals = cfg.ordersPerHour() * cfg.horizonMinutes() / 60D;
        assertTrue(Math.abs(sumOfMeans - expectedArrivals) / expectedArrivals < 0.02,
                "各格均值合计 " + sumOfMeans + " 与名义到达 " + expectedArrivals + " 差超 2%，计数有丢失");
        for (DemandCell c : cells) {
            assertTrue(c.p90() >= c.p50() && c.p50() >= 0D, c.station() + " 分位数顺序错了");
            assertTrue(c.p90() >= c.mean() - 1e-9 || c.mean() >= c.p50(), c.station() + " 均值与分位数不自洽");
            assertEquals(cfg.repeats(), c.repeats());
        }

        Files.writeString(Path.of("..", "..", "reports", "scenario-bench", "demand-m-tier.md").normalize(),
                ScenarioBench.demandReport(cfg, cells),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("  —— 站点×小时：" + cells.stream()
                .map(c -> c.station() + "@" + c.hour() + " mean=" + String.format(Locale.ROOT, "%.1f", c.mean())
                        + " P90=" + String.format(Locale.ROOT, "%.1f", c.p90()))
                .toList());
    }

    @Test
    @DisplayName("M6 热区预置：只有配对 CI 不跨 0 才允许说它有用（也可能结论是没用）")
    void repositioningIsMeasuredNotAsserted() throws Exception {
        Config base = Config.mTier(20260921L).withStationDemand(0.9D).withRepeats(12);
        Config off = base;
        Config on = base.withIdleReposition(10, 45D);
        // 第二组：同样的两臂，但补能要开车去现役那一个点（§13.16 之后这才是有物理意义的模型）
        Config offAtPile = base.withChargeLayout(Config.ChargeLayouts.singlePointSixPiles(),
                Config.PileChoice.NEAREST_FREE);
        Config onAtPile = offAtPile.withIdleReposition(10, 45D);

        List<PairedSummary> rows = ScenarioBench.comparePaired(on, off);

        assertEquals(13, rows.size());
        assertTrue(rows.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "开预置与不开逐指标相同 —— 预置分支没触发，检查空闲阈值/站点场景");
        PairedSummary relocations = rows.stream()
                .filter(s -> s.metric().equals("relocations")).findFirst().orElseThrow();
        assertTrue(relocations.diff() > 0D, "预置臂的挪车次数没有增加，说明策略没干活");

        List<PairedSummary> rowsWithLeg = ScenarioBench.comparePaired(onAtPile, offAtPile);
        assertEquals(13, rowsWithLeg.size());
        assertTrue(rowsWithLeg.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "有腿模型里开预置与不开逐指标相同 —— 预置分支在该场景下不干活");

        String report = ScenarioBench.compareReport(on, off, "热区预置 vs 不预置（M 档 · 站点场景 · skew=0.9）",
                "`idleRepositionMinutes=10`（空闲超 10 分钟的车挪到在线热区站，SOC 地板 45%）",
                "不预置（现状）", rows)
                + "\n---\n\n"
                // §13.16 补上"开去充电"这条腿之后重跑一遍：预置的收益里原本混着"补能不开车"这个失真
                + ScenarioBench.compareReport(onAtPile, offAtPile,
                "同一对预置对照，但**补能要开车过去**（现役布局）—— §13.14② 的修正数",
                "`idleRepositionMinutes=10` + 开去充电", "不预置 + 开去充电", rowsWithLeg);
        Path file = Path.of("..", "..", "reports", "scenario-bench", "repositioning-m-tier.md").normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, report, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("  —— 热区预置 vs 不预置：");
        rows.forEach(s -> System.out.println("  " + s.metric() + " diff=" + s.diff()
                + " [" + s.low() + ", " + s.high() + "] " + (s.distinguishable() ? "可分" : "分不出来")));
    }

    @Test
    @DisplayName("M4 补能选址：把\"开去充电\"这一腿加进模型，并量化 §1.8 摊开桩位的价值")
    void pileLayoutAndSelectionAreMeasuredNotAssumed() throws Exception {
        Config base = Config.mTier(20260921L).withStationDemand(0.9D).withRepeats(12);
        Config teleport = base;                                                       // 历史口径：原地瞬间开充
        Config atPile = base.withChargeLayout(Config.ChargeLayouts.singlePointSixPiles(),
                Config.PileChoice.NEAREST_FREE);                                      // 现役事实：6 桩同点，要开过去
        Config spread = base.withChargeLayout(Config.ChargeLayouts.spreadSixOverFiveStations(),
                Config.PileChoice.NEAREST_FREE);                                      // §1.8 情景：6 桩摊到 5 个站址
        Config queueAware = base.withChargeLayout(Config.ChargeLayouts.spreadSixOverFiveStations(),
                Config.PileChoice.NEAREST_INCL_QUEUE);
        Config leastLoaded = base.withChargeLayout(Config.ChargeLayouts.spreadSixOverFiveStations(),
                Config.PileChoice.LEAST_LOADED);

        // 默认臂的口径不能被这轮改动偷偷带走：无布局 ⇒ 补能里程必须恒为 0（历史基线才可复现）
        assertEquals(0D, ScenarioBench.run(teleport, teleport.seed()).chargeTravelMetersPerSession(), 1e-9,
                "无布局却算出补能里程 —— teleport 臂的口径被改掉了，§13.10~13.14 的基线就不作数了");
        assertTrue(ScenarioBench.run(atPile, atPile.seed()).chargeTravelMetersPerSession() > 0D,
                "有布局却没算开过去多远 —— beginChargingAt 那条路没走通");

        // 口径守卫（本轮真踩过：无位置布局把车赋值成桩点坐标 (0,0)，默认臂完成率 0.93 掉到 0.89 而无人察觉）：
        // 默认臂必须逐字复现 §13.10 记下的基线，否则§13.10~13.14 的全部结论一起作废。
        List<Summary> baselineRows = ScenarioBench.summarise(Config.mTier(20260921L));
        assertEquals(0.93D, metricOf(baselineRows, "completion_rate").mean(), 0.005D,
                "默认（teleport）臂的完成率漂了，§13.10 记的是 0.93 [0.90, 0.95]");
        assertEquals(138200D, metricOf(baselineRows, "total_distance_m").mean(), 2000D,
                "默认臂的总里程漂了，§13.10 记的是 138.2 km —— 加开关不许动默认口径");

        List<PairedSummary> legCost = ScenarioBench.comparePaired(atPile, teleport);
        List<PairedSummary> layoutValue = ScenarioBench.comparePaired(spread, atPile);
        List<PairedSummary> queuePolicy = ScenarioBench.comparePaired(queueAware, spread);
        List<PairedSummary> loadedPolicy = ScenarioBench.comparePaired(leastLoaded, spread);
        for (List<PairedSummary> rows : List.of(legCost, layoutValue, queuePolicy, loadedPolicy)) {
            assertEquals(13, rows.size());
        }

        // 方向留给报告下判语，这里只钉"链路确实进了模拟"：完全相同就说明参数没被消费
        assertTrue(layoutValue.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "摊开桩位后逐指标完全相同 —— chargeLayout 没进模拟");
        assertTrue(queuePolicy.stream().anyMatch(s -> Math.abs(s.diff()) > 0D)
                        || loadedPolicy.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "换选址策略后逐指标完全相同 —— pileChoice 没进模拟");

        // 容量必须对齐，否则比的是"多装了桩"而不是"选哪个桩"
        Config misaligned = base.withChargeLayout(
                List.of(new Config.ChargePoint("X", 1D, 1D, 9, true)), Config.PileChoice.NEAREST_FREE);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, misaligned::validate,
                "布局桩数合计与 chargeSlots 不一致却放行，对照就失去了可比性");

        // ④ 这一腿被漏掉时，之前的补能时机结论偏了多少：漏掉的是"每次补能约 746 m 的空驶"，
        //    所以补得越多的臂在 teleport 模型里被抬得越高。同一个对照在有/无这一腿的模型里各跑一次。
        Config neverTeleport = teleport.withChargeTiming(ScenarioBench.ChargeTiming.NEVER);
        Config neverAtPile = atPile.withChargeTiming(ScenarioBench.ChargeTiming.NEVER);
        List<PairedSummary> biasTeleport = ScenarioBench.comparePaired(teleport, neverTeleport);
        List<PairedSummary> biasWithLeg = ScenarioBench.comparePaired(atPile, neverAtPile);
        double gapWithoutLeg = pairedDiff(biasTeleport, "completion_rate");
        double gapWithLeg = pairedDiff(biasWithLeg, "completion_rate");
        assertTrue(Math.abs(gapWithLeg - gapWithoutLeg) > 1e-6D,
                "两种模型下时机对照的完成率差完全一样 —— 这一腿没进结果，检查 beginChargingAt");

        StringBuilder report = new StringBuilder();
        report.append("# 补能选址与桩位布局（M 档 · 站点场景 · skew=0.9 · 12 次重复）\n\n")
                .append("坐标来自 `t_station`（id 510/511/512/515/516，GCJ 按 `ZJF_STATIONS` 同一映射换米）。\n")
                .append("**库里实测：6 根桩 CP1..CP6 全挂在 ZJF-CHG-01 上，六个车位坐标逐字相同** —— 所以\n")
                .append("`singlePointSixPiles` 是现状，`spreadSixOverFiveStations` 是 §1.8 的情景假设。\n\n")
                .append(ScenarioBench.compareReport(atPile, teleport,
                        "① 把\"开去充电\"这一腿加进模型的代价（现役布局 vs 历史 teleport）",
                        "`chargeLayout=单点 6 桩` + `NEAREST_FREE`", "`chargeLayout=空`（原地瞬间开充）", legCost))
                .append('\n')
                .append(ScenarioBench.compareReport(spread, atPile,
                        "② 同样 6 根桩，摊到 5 个站址值多少（§1.8 的设施决策）",
                        "`spreadSixOverFiveStations`（2/1/1/1/1）", "`singlePointSixPiles`（现状）", layoutValue))
                .append('\n')
                .append(ScenarioBench.compareReport(queueAware, spread,
                        "③ 摊开之后，选址策略还值多少：最近 vs 最近+排队",
                        "`NEAREST_INCL_QUEUE`", "`NEAREST_FREE`", queuePolicy))
                .append('\n')
                .append(ScenarioBench.compareReport(leastLoaded, spread,
                        "③b 最闲桩（分散优先，距离次之）对照最近桩",
                        "`LEAST_LOADED`", "`NEAREST_FREE`", loadedPolicy))
                .append('\n')
                .append(ScenarioBench.compareReport(teleport, neverTeleport,
                        "④a 时机对照 · **无**补能行驶这一腿（历史模型，§13.12 就是在这里得的数）",
                        "`OPPORTUNISTIC` + teleport", "`NEVER` + teleport", biasTeleport))
                .append(ScenarioBench.compareReport(atPile, neverAtPile,
                        "④b 同一个时机对照 · **有**补能行驶这一腿。两表 completion_rate 那一行相减 = 漏这条腿带来的偏差\n"
                                + "实测：无这一腿 " + String.format(java.util.Locale.ROOT, "%+.4f", gapWithoutLeg)
                                + "，有这一腿 " + String.format(java.util.Locale.ROOT, "%+.4f", gapWithLeg)
                                + "，偏差 " + String.format(java.util.Locale.ROOT, "%+.4f", gapWithoutLeg - gapWithLeg),
                        "`OPPORTUNISTIC` + 单点 6 桩", "`NEVER` + 单点 6 桩", biasWithLeg));
        Path file = Path.of("..", "..", "reports", "scenario-bench", "pile-selection-m-tier.md").normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, report.toString(), java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

        System.out.println("  —— 补能行驶这一腿（atPile - teleport）：");
        legCost.stream().filter(s -> s.metric().equals("charge_travel_m_session")
                || s.metric().equals("total_distance_m") || s.metric().equals("completion_rate"))
                .forEach(s -> System.out.println("  " + s.metric() + " diff=" + s.diff()
                        + " [" + s.low() + ", " + s.high() + "] " + (s.distinguishable() ? "可分" : "分不出来")));
        System.out.println("  —— 摊开桩位（spread - atPile）：");
        layoutValue.forEach(s -> System.out.println("  " + s.metric() + " diff=" + s.diff()
                + " [" + s.low() + ", " + s.high() + "] " + (s.distinguishable() ? "可分" : "分不出来")));
    }

    @Test
    @DisplayName("M4 能耗敏感性：扫 100–250 m/1% 看派单可行域，只出参数化结论，不得称\"预测模型\"")
    void energyDrainSensitivityIsSweptNotClaimed() throws Exception {
        // 锚在"有腿 + 站点场景"上：§13.16 之后这才是现役模型，扫 teleport 会给出偏乐观的可行域。
        Config anchor = Config.mTier(20260921L).withRepeats(12).withStationDemand(0.9D)
                .withChargeLayout(Config.ChargeLayouts.singlePointSixPiles(), Config.PileChoice.NEAREST_FREE);
        double[] sweep = {100D, 150D, 200D, 250D};    // §8/M4 点名的区间；150 = 现行默认

        StringBuilder out = new StringBuilder();
        out.append("# 能耗参数敏感性（M 档 · 站点场景 skew=0.9 · 12 次重复 · 有\"开去充电\"这条腿）\n\n")
                .append("`busyDrainMetersPerPercent` = 每 1% SOC 能跑多少米。**这是敏感性扫描，不是能耗预测模型**")
                .append("（§9 口径纪律：没有真车真能耗数据，不许称模型）。\n")
                .append("默认 150 m/1%（= 满电 100% 折算 15 km 续航）。看的是它对**派单可行域**的影响：\n")
                .append("完成率、被 SOC 挡掉的候选（LOW_SOC）、无可用车的落单（NO_VEHICLE）、补能次数。\n\n")
                .append("| m/1% | 完成率 [95% CI] | LOW_SOC 次/运行 | NO_VEHICLE 次/运行 | 补能次数 | 补能排队车·分钟 |\n")
                .append("| --- | --- | --- | --- | --- | --- |\n");

        double rateAt100 = Double.NaN;
        double rateAt250 = Double.NaN;
        for (double drain : sweep) {
            Config cfg = anchor.withEnergyDrain(drain);
            List<Summary> rows = ScenarioBench.summarise(cfg);
            Summary rate = metricOf(rows, "completion_rate");
            java.util.Map<String, Double> fails = ScenarioBench.failureMeans(cfg);
            out.append(String.format(java.util.Locale.ROOT, "| %.0f | %.4f [%.4f, %.4f] | %.2f | %.2f | %.1f | %.1f |%n",
                    drain, rate.mean(), rate.low(), rate.high(),
                    fails.getOrDefault("LOW_SOC", 0D), fails.getOrDefault("NO_VEHICLE", 0D),
                    metricOf(rows, "charge_sessions").mean(),
                    metricOf(rows, "charge_blocked_car_min").mean()));
            if (drain == 100D) {
                rateAt100 = rate.mean();
            }
            if (drain == 250D) {
                rateAt250 = rate.mean();
            }
        }

        // 单调性：省电（m/1% 大 = 同样电量跑更远）不该让完成率变差。
        // 这条不是为了"证明结论"，是为了挡住"参数接反/接错单位"这类静默失效。
        assertTrue(rateAt250 >= rateAt100 - 1e-9D,
                "更省的能耗参数反而完成率更低：先查 withEnergyDrain 的单位与 metersToSoc 的方向");
        double span = rateAt250 - rateAt100;
        out.append("\n**从 100 到 250 m/1%（2.5 倍跨度）完成率的总变化 = ")
                .append(String.format(java.util.Locale.ROOT, "%+.4f", span))
                .append("**，两值分别 ").append(String.format(java.util.Locale.ROOT, "%.4f / %.4f", rateAt100, rateAt250))
                .append("。这个量级就是\"能耗参数标不准\"能给**完成率类结论**带来的最大扰动：")
                .append("小于它的完成率差，不足以归因给策略（它同样可以是 2.5 倍能耗不确定度里的任意一档）。")
                .append("对里程类结论不构成同量级的界，别混用。\n");

        Path file = Path.of("..", "..", "reports", "scenario-bench", "energy-sensitivity-m-tier.md").normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, out.toString(), java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        System.out.printf(Locale.ROOT, "[scenario-bench] 能耗敏感性写出 %s（100→250 完成率 %+.4f）%n",
                file.toAbsolutePath(), span);
    }

    @Test
    @DisplayName("M4 充电曲线：默认臂必须逐字等于历史线性口径，给了拐点则占桩更久、完成率下降")
    void chargeCurveIsScenarioSensitivityNotACalibratedModel() throws Exception {
        Config anchor = Config.mTier(20260921L).withRepeats(12).withStationDemand(0.9D)
                .withChargeLayout(Config.ChargeLayouts.singlePointSixPiles(), Config.PileChoice.NEAREST_FREE);

        // ① 算术先对上：LINEAR 就是历史那条折算式 1800 × (90-50)/(90-20)
        assertEquals(1800D * 40 / 70, anchor.chargeSecondsFor(50, 90), 1e-9,
                "默认曲线改变了历史折算口径");
        assertTrue(anchor.withChargeCurve(Config.ChargeCurve.TAPER_80_HALF).chargeSecondsFor(50, 90)
                > anchor.chargeSecondsFor(50, 90), "拐点之上的部分必须更慢");
        // 拐点在目标之上时，任何倍率都不该改变任何东西（LINEAR 的定义域守卫）
        assertEquals(1800D * 10 / 70,
                anchor.withChargeCurve(new Config.ChargeCurve(95D, 9D)).chargeSecondsFor(80, 90), 1e-9,
                "拐点高于目标 SOC 时不该有慢充段");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new Config.ChargeCurve(80D, 0.5D), "慢充段比快充还快不是物理上说得通的参数");

        // ② 加开关不得动默认口径：不传曲线的臂与显式传 LINEAR 的臂必须逐指标相同
        List<Summary> implicit = ScenarioBench.summarise(anchor);
        List<Summary> explicit = ScenarioBench.summarise(anchor.withChargeCurve(Config.ChargeCurve.LINEAR));
        for (int i = 0; i < implicit.size(); i++) {
            assertEquals(implicit.get(i).mean(), explicit.get(i).mean(), 0D,
                    implicit.get(i).metric() + " 默认臂与 LINEAR 臂不同 —— 说明默认值没有真正退回历史口径");
        }

        // ③ 敏感性扫描：拐点 80%、慢 2×/3×，看"占桩更久"值多少完成率
        Config half = anchor.withChargeCurve(Config.ChargeCurve.TAPER_80_HALF);
        Config third = anchor.withChargeCurve(Config.ChargeCurve.TAPER_80_THIRD);
        List<PairedSummary> vsHalf = ScenarioBench.comparePaired(half, anchor);
        List<PairedSummary> vsThird = ScenarioBench.comparePaired(third, anchor);
        assertEquals(13, vsHalf.size());
        assertTrue(vsHalf.stream().anyMatch(s -> Math.abs(s.diff()) > 0D),
                "改曲线却逐指标相同 —— chargeCurve 没接进 chargeTicks");
        PairedSummary completion = metric(vsHalf, "completion_rate");
        assertTrue(completion.diff() <= 0D,
                "尾部更慢却让完成率上升，方向可疑：" + completion.diff());

        StringBuilder out = new StringBuilder();
        out.append("# 充电曲线敏感性（M 档 · 站点场景 skew=0.9 · 12 次重复 · 现役单点 6 桩布局）\n\n")
                .append("**拐点与倍率都是情景设定，不是拟合**：仓库里没有真车真充电记录（§0.2），")
                .append("所以这一张表只回答\"如果尾部真的慢 2×/3×，占桩时间与完成率会动多少\"，")
                .append("不回答\"真实曲线是什么样\"。默认 `LINEAR` 与历史口径逐字相同（测试里逐指标断言）。")
                .append("慢充段定义：SOC 高于拐点的部分按 1/倍率 的速率充。\n\n")
                .append(ScenarioBench.compareReport(half, anchor,
                        "① 拐点 80%、之后慢 2 倍 vs 现行线性", "`TAPER_80_HALF`", "`LINEAR`（现状）", vsHalf))
                .append(ScenarioBench.compareReport(third, anchor,
                        "② 同一拐点、慢到三分之一", "`TAPER_80_THIRD`", "`LINEAR`（现状）", vsThird))
                .append(ScenarioBench.compareReport(third, half,
                        "③ 两个情景之间（用来估计\"曲线到底多不确定\"）",
                        "`TAPER_80_THIRD`", "`TAPER_80_HALF`", ScenarioBench.comparePaired(third, half)));
        Path file = Path.of("..", "..", "reports", "scenario-bench", "charge-curve-m-tier.md").normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, out.toString(), java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("  —— 曲线慢 2× vs 线性：");
        vsHalf.forEach(s -> System.out.println("  " + s.metric() + " diff=" + s.diff()
                + " [" + s.low() + ", " + s.high() + "] " + (s.distinguishable() ? "可分" : "分不出来")));
    }

    private static PairedSummary metric(List<PairedSummary> rows, String name) {
        return rows.stream().filter(s -> s.metric().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("S 档（3 台车）的完成率必须明显低于 M 档，档位之间不可混用数字")
    void tiersBehaveDifferently() {
        Config m = Config.mTier(5L).withRepeats(3);
        Config s = Config.sTier(5L).withRepeats(3);

        double mRate = ScenarioBench.run(m, m.seed()).completed() / (double) Math.max(1, ScenarioBench.run(m, m.seed()).arrived());
        RunResult sRun = ScenarioBench.run(s, s.seed());

        assertTrue(mRate > sRun.completed() / (double) Math.max(1, sRun.arrived()),
                "S 档没有体现出运力不足，档位标签就只是装饰");
    }

    private static double pairedDiff(List<ScenarioBench.PairedSummary> rows, String metric) {
        return rows.stream().filter(s -> s.metric().equals(metric)).findFirst().orElseThrow().diff();
    }

    @Test
    @DisplayName("M5 L 档退化曲线：两个轴分开看（需求固定看运力边际、需求同比放大看规模不变性）")
    void lTierDegradationCurveIsMeasuredNotExtrapolated() throws Exception {
        Config base = Config.mTier(20260921L).withRepeats(12);
        int[] fleets = {10, 20, 40};

        List<String> axisA = new ArrayList<>();
        List<String> axisB = new ArrayList<>();
        List<Summary> rateA = new ArrayList<>();
        List<Summary> rateB = new ArrayList<>();
        List<Double> candidatesA = new ArrayList<>();
        List<Double> candidatesB = new ArrayList<>();
        List<Double> blockedB = new ArrayList<>();

        for (int size : fleets) {
            List<Summary> fixedDemand = ScenarioBench.summarise(base.withFleetSize(size));
            List<Summary> scaledDemand = ScenarioBench.summarise(
                    base.withFleetSize(size).withOrdersPerHour(56 * size / 20));
            assertTrue(metricOf(fixedDemand, "completed_orders").mean() > 0, size + " 台一单都没完成");
            axisA.add(row("A 需求固定 56/h · " + size + " 台", fixedDemand));
            axisB.add(row("B 需求随车数同比 · " + size + " 台 / " + (56 * size / 20) + " 单每小时", scaledDemand));
            candidatesA.add(metricOf(fixedDemand, "candidates_per_decision").mean());
            candidatesB.add(metricOf(scaledDemand, "candidates_per_decision").mean());
            blockedB.add(metricOf(scaledDemand, "charge_blocked_car_min").mean());
            rateA.add(metricOf(fixedDemand, "completion_rate"));
            rateB.add(metricOf(scaledDemand, "completion_rate"));
        }

        double marginalFirst = (rateA.get(1).mean() - rateA.get(0).mean()) * 100D;
        double marginalSecond = (rateA.get(2).mean() - rateA.get(1).mean()) * 100D;
        double scaleRatio = candidatesB.get(2) / candidatesB.get(1);
        Summary saturated = rateA.get(2);

        StringBuilder out = new StringBuilder();
        out.append("# L 档退化曲线（12 次重复 · 95% t-CI）\n\n")
                .append("**这不是 §1.3 的 L 目标规模档**：范围仍是现役 1613×500 m、桩仍是 6 根，只有车队与需求在动。\n")
                .append("所以本表读作\"运力的边际收益 / 规模放大时的不变性\"，不得当作 40 台 @1.9 km²/10 桩的容量证明\n")
                .append("（§1.3 档位不可混用；§9 禁止外推到 500 台）。\n\n")
                .append("## A 轴：需求固定在 56 单每小时，只加车 —— 看每一台车的边际收益\n\n")
                .append("| 配置 | 完成率 [95% CI] | 完成时长均值 | 总里程 | 补能被挡(车·分) | 空驶率 | 候选/次派单 |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        axisA.forEach(out::append);
        out.append("\n## B 轴：需求随车数同比放大（保持运力/需求密度不变）—— 看规模本身会不会自带退化\n\n")
                .append("| 配置 | 完成率 [95% CI] | 完成时长均值 | 总里程 | 补能被挡(车·分) | 空驶率 | 候选/次派单 |\n")
                .append("| --- | --- | --- | --- | --- | --- | --- |\n");
        axisB.forEach(out::append);
        out.append(String.format(java.util.Locale.ROOT, """


                ## 怎么读（下面每个数都由上表算出，方向由测试断言钉住）

                **A 轴：完成率在 40 台这一档饱和了** —— 均值 %.4f，95%% CI [%.4f, %.4f]（宽度 %.4f）。
                加车的边际收益在衰减：10→20 台 %+.2f pp，20→40 台 %+.2f pp（后者是前者的 %.0f%%）。
                也就是说 40 台买到的不是"更多单"而是"更多空闲车"：那一档每次派单平均扫到 %.1f 台空闲车，
                约占车队的 %.0f%%。所以 A 轴最后一列读作 abundance 而不是 algorithm，
                拿它当"L 档决策成本"会高估（需求没跟着涨，车当然闲着）。

                **B 轴才是规模读法**：需求同比放大时完成率不退化反而升（%.4f → %.4f → %.4f），
                所以在现役范围和 6 根桩下，**规模本身不吃完成率**。但同一张表里两列在超线性恶化：

                - 补能被挡：%.1f → %.1f → %.1f 车·分。车只有 4× 而阻塞涨了 %.0f×。
                - 候选/次派单：%.1f → %.1f → %.1f，40 台是 20 台的 %.1f×。

                桩侧压力和决策成本都在**完成率看不见的位置**上涨。这就是 L 档真正先撑不住的地方，
                也是 §1.4 那条"热路径 277 ms 必须按候选集重测"的依据：按 B 轴口径，
                40 台时每次派单要扫的车约是 20 台的 %.1f 倍。
                生产侧已核实是同一个形状：候选集就是**全量在线空闲车**（`DispatchVehicleAssignServiceImpl:165`
                的 `listAssignableVehicles()`，没有距离/半径预筛），而每台候选要跑两次路径规划
                （`canCompleteTaskWithSoc` 的可达性 + `estimateRouteDistance`，:198-216）——
                所以候选 ×%.1f 就是热路径耗时 ×%.1f，减候选（距离预筛 / 空间索引）是 L 档最直接的杠杆。

                **等派那一列被去掉了，原因要记着**：批量窗口=1 时接不上的单直接判 NO_VEHICLE 丢弃、不入队
                （已核实主循环：backlog 每 tick 必冲，冲不动就记失败），所以该列结构性恒为 0。
                它测的不是"没有等待"，而是"等待没有被建模"。缺单的代价记在完成率里，
                要看等派只能在窗口 >1 的配置下看（见 batch 报告）。

                本表的候选/次派单只是规模代理，不是时延。时延要用 `scripts/dev/bench-dispatch-latency.sh`
                在真实服务上测，而且 MAPF 修复后旧数字已不可比（占用口径变了），必须重测后再引用。
                """,
                saturated.mean(), saturated.low(), saturated.high(), saturated.high() - saturated.low(),
                marginalFirst, marginalSecond, 100D * marginalSecond / marginalFirst,
                candidatesA.get(2), 100D * candidatesA.get(2) / fleets[2],
                rateB.get(0).mean(), rateB.get(1).mean(), rateB.get(2).mean(),
                blockedB.get(0), blockedB.get(1), blockedB.get(2), blockedB.get(2) / blockedB.get(0),
                candidatesB.get(0), candidatesB.get(1), candidatesB.get(2),
                scaleRatio, scaleRatio, scaleRatio, scaleRatio));

        Path file = Path.of("..", "..", "reports", "scenario-bench", "l-tier-degradation.md").normalize();
        Files.createDirectories(file.getParent());
        Files.writeString(file, out.toString(), java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
        System.out.println("  —— L 退化曲线写出 " + file.toAbsolutePath());

        // 判语在表落盘之后再钉：即使方向不成立，也要留下实测数字去查为什么，而不是只留下一条红。
        // 上面散文里每个方向断言都在这里，二者必须同生共死。
        assertTrue(candidatesA.get(2) > candidatesA.get(0) * 2D,
                "车队 4 倍于 10 台，但每次派单评估的候选数没同比涨：" + candidatesA);
        assertTrue(saturated.mean() > 0.99D, "A 轴 40 台没饱和，\"边际收益衰减\"那段散文不成立: " + saturated);
        assertTrue(marginalSecond > 0D && marginalSecond < marginalFirst,
                "加车没有出现边际收益递减（首段 " + marginalFirst + " pp / 次段 " + marginalSecond + " pp）");
        assertTrue(rateB.get(2).mean() >= rateB.get(0).mean(),
                "B 轴（需求同比放大）出现完成率退化 —— 规模本身成了约束，L 档结论要重写: " + rateB);
        assertTrue(blockedB.get(2) > blockedB.get(1) * 2D,
                "补能阻塞没有随规模超线性上涨，\"桩侧压力先撑不住\"那句不成立: " + blockedB);
    }

    private static String row(String label, List<Summary> rows) {
        Summary rate = metricOf(rows, "completion_rate");
        return String.format(java.util.Locale.ROOT,
                "| %s | %.4f [%.4f, %.4f] | %.1f s | %.1f km | %.1f | %.2f | %.1f |%n",
                label, rate.mean(), rate.low(), rate.high(),
                metricOf(rows, "completion_mean_s").mean(),
                metricOf(rows, "total_distance_m").mean() / 1000D,
                metricOf(rows, "charge_blocked_car_min").mean(),
                metricOf(rows, "deadhead_share").mean(),
                metricOf(rows, "candidates_per_decision").mean());
    }

    private static Summary metricOf(List<Summary> rows, String metric) {
        return rows.stream().filter(s -> s.metric().equals(metric)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("t 分位数表按 df=n-1 取值（曾经整段写成 df=n，导致区间系统性偏窄）")
    void studentTUsesNMinusOneDegreesOfFreedom() {
        assertEquals(12.706D, ScenarioBench.studentT(2), 1e-3, "n=2 应取 t(df=1)");
        assertEquals(2.776D, ScenarioBench.studentT(5), 1e-3, "n=5 应取 t(df=4)");
        assertEquals(2.571D, ScenarioBench.studentT(6), 1e-3, "n=6 应取 t(df=5)，旧表在这里开始错位");
        assertEquals(2.365D, ScenarioBench.studentT(8), 1e-3, "n=8 应取 t(df=7)");
        assertEquals(2.201D, ScenarioBench.studentT(12), 1e-3, "n=12 是全部基准用的档，应取 t(df=11)");
        assertEquals(2.093D, ScenarioBench.studentT(21), 1e-3, "表外的 n 必须退回更小的一档（偏宽不偏窄）");
        assertEquals(1.96D, ScenarioBench.studentT(40), 1e-3, "n>=30 用正态近似");
        for (int n = 3; n <= 30; n++) {
            assertTrue(ScenarioBench.studentT(n) <= ScenarioBench.studentT(n - 1) + 1e-9,
                    "t 表在 n=" + n + " 处不单调，说明有档位抄错");
        }
    }
}

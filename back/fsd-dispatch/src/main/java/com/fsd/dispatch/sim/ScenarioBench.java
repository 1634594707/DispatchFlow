package com.fsd.dispatch.sim;

import com.fsd.dispatch.core.DecisionInput;
import com.fsd.dispatch.core.DecisionOutcome;
import com.fsd.dispatch.core.DecisionPolicy;
import com.fsd.dispatch.core.DecisionWeights;
import com.fsd.dispatch.core.RankedCandidate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * 仿真实验台（§5 / M3）。
 *
 * <p>为什么要它：仓库里没有真车（§0.2 车辆表只有 3 台仿真车、遥测停在 2026-08-22），
 * 所以任何"算法改进有没有收益"的说法只能来自可复现的仿真；而 §0.1 已经证明单次压测点值
 * 的方差大到不能当证据（同一配置两次 P95 差近 2 倍）。这里给出的是：固定种子、N 次重复、
 * 带置信区间的指标表 + 事后最优基线。
 *
 * <p>决策用的是生产同一份 {@link com.fsd.dispatch.core.RulePolicy}（纯函数、无 Spring），
 * 所以这里测到的差异就是算法差异，不是两套实现的差异。
 *
 * <p><b>模型是简化的</b>，所有简化都写进 {@link #assumptions()} 并随报告一起落盘：
 * 直线距离 × 绕行系数、固定服务时长、充电线性、无路口冲突。它用来比较策略之间的相对差异，
 * 不用来宣称绝对数值。
 */
public final class ScenarioBench {

    /** 撮合方式：现状是"逐单贪心"（一单一单派，后一单看到前一单改过的车队状态）。 */
    public enum MatchStrategy { SEQUENTIAL_GREEDY, HUNGARIAN }

    /** 需求的空间结构：站点集合 + 热区强度。默认 UNIFORM 是园区内随机点。 */
    public record Demand(List<Station> pickups, List<Station> dropoffs, double skew) {

        /** 现状默认：取送货点在园区内均匀随机，用于撮合/补能类的对照实验。 */
        public static final Demand UNIFORM = new Demand(List.of(), List.of(), 0D);

        /** §1.8 建好 4 个站点后，这里要换成库里 ACTIVE 的 PICKUP/DROPOFF 实测坐标。 */
        public static final Demand ZJF_STATIONS = new Demand(
                List.of(new Station("ZJF-PICK-01", 294.1D, 438.9D),
                        new Station("ZJF-PICK-02", 120.0D, 402.2D)),
                List.of(new Station("ZJF-DROP-01", 795.5D, 79.3D),
                        new Station("ZJF-DROP-02", 1479.5D, 284.9D),
                        new Station("ZJF-DROP-03", 285.9D, 88.1D),
                        new Station("ZJF-DROP-04", 1185.6D, 167.6D)),
                0D);

        public boolean stationBased() {
            return !pickups.isEmpty() && !dropoffs.isEmpty();
        }

        /** 按 rank 的 Zipf 权重抽站点；skew=0 即均匀。热区强度就是这个参数，必须随报告一起声明。 */
        Station pick(List<Station> pool, double skew, Random rng) {
            double[] w = new double[pool.size()];
            double total = 0D;
            for (int i = 0; i < pool.size(); i++) {
                w[i] = 1.0D / Math.pow(i + 1.0D, skew);
                total += w[i];
            }
            double roll = rng.nextDouble() * total;
            double acc = 0D;
            for (int i = 0; i < pool.size(); i++) {
                acc += w[i];
                if (roll <= acc) {
                    return pool.get(i);
                }
            }
            return pool.get(pool.size() - 1);
        }
    }

    /** 站点在场景坐标系里的位置：由库内 GCJ-02 按 `x=(lng-121.072610)*94421+120, y=(31.963800-lat)*111320+60` 平移而来。 */
    public record Station(String code, double x, double y) {
    }

    /**
     * 需求的时间结构。默认 FLAT 是齐次泊松 —— 那种"小时之间的差异"只是抽样噪声，
     * 不能当峰谷用（§5 第 1 条的"时段分布"缺的就是这个）。
     *
     * @param peakStartHour 高峰起始小时（含），按仿真时钟从 0 计
     * @param peakEndHour   高峰结束小时（不含）
     * @param multiplier    高峰时段的到达强度倍数
     */
    public record ArrivalProfile(int peakStartHour, int peakEndHour, double multiplier) {

        public static final ArrivalProfile FLAT = new ArrivalProfile(0, 0, 1.0D);

        /** 园区节奏：第 2 个小时（仿真时钟 1..2 点）强度翻倍，视野内就有峰段与平段两块可比。 */
        public static final ArrivalProfile PEAK_SECOND_HOUR = new ArrivalProfile(1, 2, 2.0D);

        double rateAt(int hour) {
            boolean peak = peakEndHour > peakStartHour && hour >= peakStartHour && hour < peakEndHour;
            return peak ? multiplier : 1.0D;
        }
    }

    /** 一个 tick = 15 秒。 */
    public static final int TICK_SECONDS = 15;

    /** 补能时机策略，对齐 {@code FleetEnergyProperties} 与 {@code EnergyForecastServiceImpl} 的语义。 */
    public enum ChargeTiming {
        /** 只有低于 {@code returnToChargeSoc} 才回桩（几乎不回，用来给"补不补"定下界）。 */
        NEVER,
        /** 现状 {@code idleChargeWhenNoDemand=true}：本 tick 没有落单就顺势补，只抢空桩不排队。 */
        OPPORTUNISTIC,
        /** 错峰：近 {@code pressureWindowTicks} 内落单数达到阈值时，连顺势补能也推迟（必充仍不推迟，安全优先）。 */
        DEFER_UNDER_PRESSURE,
        /** 真错峰：只在**需求高峰时段**（由 {@link ArrivalProfile} 定义）推迟顺势补能，平段照常 —— 对齐 `shouldDeferReturnToCharge`。 */
        DEFER_IN_PEAK
    }

    /** §5 第 1 条要的"场景配置外置"：档位、运力、需求、SOC 阈值、能耗、范围、路网版本、种子全在这里。 */
    public record Config(String tier,
                         String roadGraphVersion,
                         int vehicles,
                         int ordersPerHour,
                         int horizonMinutes,
                         int repeats,
                         long seed,
                         int minAssignableSoc,
                         int returnToChargeSoc,
                         int chargeCompleteSoc,
                         ChargeTiming chargeTiming,
                         MatchStrategy matchStrategy,
                         int matchWindowTicks,
                         int pressureWindowTicks,
                         int pressureDeferOrders,
                         int fullSoc,
                         double busyDrainMetersPerPercent,
                         double detourFactor,
                         double avgSpeedKmh,
                         int serviceSeconds,
                         int chargeSeconds,
                         int chargeSlots,
                         double parkWidthM,
                         double parkHeightM,
                         Demand demand,
                         ArrivalProfile arrival,
                         int idleRepositionMinutes,
                         double repositionSocFloor,
                         List<ChargePoint> chargeLayout,
                         PileChoice pileChoice,
                         ChargeCurve chargeCurve) {

        /**
         * 距离/速度/绕行系数三项来自 {@code back/sql/seed/zjf_road_network.sql} 头部统计
         * （见 §13.9.2 的裁断后数字）。换路网就换这个串，报告里能看出数字是哪版图跑出来的。
         */
        public static final String ROAD_GRAPH_OSM_EXPANDED = "osm-expanded-2026-09-21/91n-113e-24354m";

        /** §1.3 的 M 档默认场景：20 台车 / 56 单每小时 / 2 小时 / 6 桩，范围取现役派单围栏外接框。 */
        public static Config mTier(long seed) {
            return new Config("M", ROAD_GRAPH_OSM_EXPANDED, 20, 56, 120, 12, seed, 30, 20, 90,
                    ChargeTiming.OPPORTUNISTIC, MatchStrategy.SEQUENTIAL_GREEDY, 1, 20, 3, 100,
                    150D, 1.481D, 17.84D, 474, 1800, 6, 1613D, 500D,
                    Demand.UNIFORM, ArrivalProfile.FLAT, 0, 40D,
                    List.of(), PileChoice.NEAREST_FREE, ChargeCurve.LINEAR);
        }

        /**
         * M6 用：OD 落在库内真实站点上（当前 2 取货 + 4 送货，§1.8 那 4 个还没进库）。
         *
         * @param skew 热区强度（Zipf 指数，0 = 各站点等可能）。这是**假设参数**，不是实测：
         *             仓库里没有真车真单（§0.2），所以"热区有多热"只能作为情景设定并随报告声明。
         */
        public Config withStationDemand(double skew) {
            return copy(b -> b.demand = new Demand(Demand.ZJF_STATIONS.pickups(), Demand.ZJF_STATIONS.dropoffs(), skew));
        }

        /** §5 第 1 条的"时段分布"：换成有峰段的需求过程。 */
        public Config withPeakDemand() {
            return copy(b -> b.arrival = ArrivalProfile.PEAK_SECOND_HOUR);
        }

        /** M6 预置策略：空闲超过这么多分钟的车，往"当前接单最多的取货站"挪（0 = 关）。 */
        public Config withIdleReposition(int idleMinutes, double socFloor) {
            return copy(b -> {
                b.idleRepositionMinutes = idleMinutes;
                b.repositionSocFloor = socFloor;
            });
        }

        /**
         * 补能选址策略。
         *
         * <p>只在 {@link Config#chargeLayout()} 非空时生效 —— 现行默认没有"桩位在哪"这个量，
         * 车在原地瞬间开始充电（teleport），任何选址都无从谈起。
         */
        public enum PileChoice {
            /** 最近空闲桩：只看开过去多远。 */
            NEAREST_FREE,
            /** 最近 + 排队：把"等到有位"的时间折进距离一起比。 */
            NEAREST_INCL_QUEUE,
            /** 最闲桩：距离不动，只把车摊到会话数最少的点上（线上 `sort_order` 先来先服务的对照面）。 */
            LEAST_LOADED
        }

        /**
         * 一个可充电的地点：位置（park 局部米制，同 {@link Demand#ZJF_STATIONS} 口径）+ 该点桩位数。
         *
         * @param measured 该点坐标是否真的存在于库里（false = §1.8 的情景假设）
         */
        public record ChargePoint(String code, double x, double y, int piles, boolean measured) {
        }

        /** 补能布局预设。坐标全部由 {@code t_station} 的 GCJ 经纬度按 ZJF_STATIONS 同一线性映射换米。 */
        public static final class ChargeLayouts {

            /** 库里 5 个 CHARGING_STATION 的真实位置（id 510/511/512/515/516）。 */
            private static final ChargePoint[] STATIONS = {
                    new ChargePoint("ZJF-CHG-01", 882.1D, 445.5D, 1, true),
                    new ChargePoint("ZJF-CHG-02", 797.0D, 91.4D, 1, true),
                    new ChargePoint("ZJF-CHG-03", 120.0D, 71.1D, 1, true),
                    new ChargePoint("ZJF-CHG-04", 293.0D, 408.3D, 1, true),
                    new ChargePoint("ZJF-CHG-05", 1226.8D, 161.3D, 1, true)
            };

            private ChargeLayouts() {
            }

            /**
             * 现役事实：6 根桩 CP1..CP6 全挂在 ZJF-CHG-01 上，且 P1..P6 六个车位坐标**逐字相同**
             * （库里实测 668.4/624.5 px 一个点）。所以今天不存在"选近的桩"这回事，只有一个点上的排队。
             */
            public static List<ChargePoint> singlePointSixPiles() {
                return List.of(new ChargePoint("ZJF-CHG-01", STATIONS[0].x(), STATIONS[0].y(), 6, true));
            }

            /** §1.8 情景：同样 6 根桩摊到 5 个真实站址上（2/1/1/1/1，主站给 2 根）。 */
            public static List<ChargePoint> spreadSixOverFiveStations() {
                List<ChargePoint> spread = new java.util.ArrayList<>(STATIONS.length);
                for (int i = 0; i < STATIONS.length; i++) {
                    spread.add(i == 0 ? new ChargePoint(STATIONS[i].code(), STATIONS[i].x(), STATIONS[i].y(), 2, true)
                            : STATIONS[i]);
                }
                return List.copyOf(spread);
            }
        }

        /** M4 能耗只做**参数化 + 敏感性**：改"每 1% SOC 跑多少米"，不称"预测模型"（§9 的口径纪律）。 */
        public Config withEnergyDrain(double metresPerPercent) {
            return copy(b -> b.busyDrainMetersPerPercent = metresPerPercent);
        }

        /**
         * 打开"开去充电"这一腿。传入空列表即回到现行 teleport 语义（历史基线因此仍可复现）。
         *
         * <p>{@code cfg.chargeSlots()} 在布局模式下不再独立生效：总桩位数由布局各点相加决定，
         * 二者不一致时 {@link Config#validate()} 直接拒绝，避免跑出对不上账的对照。
         */
        public Config withChargeLayout(List<ChargePoint> layout, PileChoice choice) {
            return copy(b -> {
                b.chargeLayout = List.copyOf(layout);
                b.pileChoice = choice;
            });
        }

        /**
         * 充电曲线的**形状**参数（M4「充电曲线分段」）。
         *
         * <p>现役模型是线性的：`1800 s ×（90 − SOC）/（90 − 20）`，即"最深一次补能 30 分钟"，
         * 每 1% 花的时间一样。真实锂电在 SOC 高了之后会转恒压、电流下降，所以尾部更慢 ——
         * 尾部慢意味着**同一根桩被占更久**，直接打在"6 根桩"这个硬上限上。
         *
         * <p>本参数只做**敏感性**，不做拟合：仓库里没有真车真充电曲线数据（§0.2），
         * 拐点与倍率都是情景设定，必须随报告声明（与 §9 对能耗的那条纪律同一口径）。
         *
         * @param kneeSoc       拐点：SOC 高于这个值进入慢充段（{@code ≥ 目标 SOC} 即完全等价于线性）
         * @param taperDivisor  慢充段的速率分母（2 = 慢一倍）。必须 ≥ 1。
         */
        public record ChargeCurve(double kneeSoc, double taperDivisor) {

            /** 现行口径：全程等速。默认臂用它，历史数字才可复现。 */
            public static final ChargeCurve LINEAR = new ChargeCurve(Double.MAX_VALUE, 1D);

            /** 情景 80% 后慢一倍 / 慢到三分之一。 */
            public static final ChargeCurve TAPER_80_HALF = new ChargeCurve(80D, 2D);
            public static final ChargeCurve TAPER_80_THIRD = new ChargeCurve(80D, 3D);

            public ChargeCurve {
                if (taperDivisor < 1D) {
                    throw new IllegalArgumentException("taperDivisor 只能 ≥ 1（慢充不可能比快充还快）: " + taperDivisor);
                }
            }
        }

        /**
         * 换充电曲线。默认 {@link ChargeCurve#LINEAR} —— 用它必须逐字复现历史基线（测试里钉着）。
         */
        public Config withChargeCurve(ChargeCurve curve) {
            return copy(b -> b.chargeCurve = curve);
        }

        /** 一次补能需要的秒数：慢充段按分母拉长，线性段按原标定不变。 */
        public double chargeSecondsFor(int fromSoc, int toSoc) {
            double span = Math.max(1D, toSoc - returnToChargeSoc);
            double fastRatePointsPerSecond = span / Math.max(1, chargeSeconds);   // 线性标定：这段这么快
            double need = Math.max(0D, toSoc - fromSoc);
            double inSlowZone = Math.max(0D, Math.min(need, toSoc - Math.max(fromSoc, chargeCurve.kneeSoc())));
            double inFastZone = need - inSlowZone;
            return inFastZone / fastRatePointsPerSecond
                    + inSlowZone / (fastRatePointsPerSecond / chargeCurve.taperDivisor());
        }

        /** 布局模式下的总桩数：各点之和；非布局模式返回 0。 */
        public int layoutPileCount() {
            return chargeLayout == null ? 0 : chargeLayout.stream().mapToInt(ChargePoint::piles).sum();
        }

        /** §1.3 的 S 档：现况 3 台车、1 小时窗口，只用于冒烟与"档位不可混用"的对照。 */
        public static Config sTier(long seed) {
            return mTier(seed).withTier("S", 3, 60);
        }

        Config withTier(String tier, int vehicles, int horizonMinutes) {
            return copy(b -> {
                b.tier = tier;
                b.vehicles = vehicles;
                b.horizonMinutes = horizonMinutes;
            });
        }

        /** §1.3 的 L 档：40 台。**范围与桩数仍取现役值**（1613×500 m、6 桩），
         *  所以这组数字测的是"运力增加时的退化/边际收益曲线"，不是 §1.3 那个 1.9 km²/10 桩的目标规模档。
         *  把它当目标规模引用即为违规（§1.3 档位不可混用）。 */
        public static Config lTier(long seed) {
            return mTier(seed).withTier("L", 40, 120);
        }

        /** 退化曲线用：只改车队规模，需求与其余参数逐字不动。 */
        public Config withFleetSize(int vehicles) {
            return copy(b -> b.vehicles = vehicles);
        }

        /** 退化曲线用：只改需求强度（单/小时）。 */
        public Config withOrdersPerHour(int ordersPerHour) {
            return copy(b -> b.ordersPerHour = ordersPerHour);
        }

        public Config withRepeats(int n) {
            return copy(b -> b.repeats = n);
        }

        public Config withChargeTiming(ChargeTiming timing) {
            return copy(b -> b.chargeTiming = timing);
        }

        public Config withMatchStrategy(MatchStrategy strategy) {
            return copy(b -> b.matchStrategy = strategy);
        }

        /** 批量撮合的决策窗口（tick）。1 = 来一单派一单（现状）；>1 = 攒够一段时间再一次性配对。 */
        public Config withMatchWindow(int windowTicks) {
            return copy(b -> b.matchWindowTicks = Math.max(1, windowTicks));
        }

        /** 敏感性检查用：把推迟条件调到很凶，验证"错峰没有收益"不是因为分支几乎不触发。 */
        public Config withPressureWindow(int windowTicks, int deferOrders) {
            return copy(b -> {
                b.pressureWindowTicks = windowTicks;
                b.pressureDeferOrders = deferOrders;
            });
        }

        /** with* 全部走这条路，避免 22 参数的构造调用在各处抄一遍。 */
        private Config copy(java.util.function.Consumer<Builder> mutator) {
            Builder b = new Builder(this);
            mutator.accept(b);
            return new Config(b.tier, b.roadGraphVersion, b.vehicles, b.ordersPerHour, b.horizonMinutes,
                    b.repeats, b.seed, b.minAssignableSoc, b.returnToChargeSoc, b.chargeCompleteSoc,
                    b.chargeTiming, b.matchStrategy, b.matchWindowTicks, b.pressureWindowTicks,
                    b.pressureDeferOrders, b.fullSoc,
                    b.busyDrainMetersPerPercent, b.detourFactor, b.avgSpeedKmh, b.serviceSeconds,
                    b.chargeSeconds, b.chargeSlots, b.parkWidthM, b.parkHeightM,
                    b.demand, b.arrival, b.idleRepositionMinutes, b.repositionSocFloor,
                    b.chargeLayout, b.pileChoice, b.chargeCurve);
        }

        private static final class Builder {
            String tier;
            String roadGraphVersion;
            int vehicles;
            int ordersPerHour;
            int horizonMinutes;
            int repeats;
            long seed;
            int minAssignableSoc;
            int returnToChargeSoc;
            int chargeCompleteSoc;
            ChargeTiming chargeTiming;
            MatchStrategy matchStrategy;
            int matchWindowTicks;
            int pressureWindowTicks;
            int pressureDeferOrders;
            int fullSoc;
            double busyDrainMetersPerPercent;
            double detourFactor;
            double avgSpeedKmh;
            int serviceSeconds;
            int chargeSeconds;
            int chargeSlots;
            double parkWidthM;
            double parkHeightM;
            Demand demand;
            ArrivalProfile arrival;
            int idleRepositionMinutes;
            double repositionSocFloor;
            List<ChargePoint> chargeLayout;
            PileChoice pileChoice;
            ChargeCurve chargeCurve;

            Builder(Config c) {
                tier = c.tier;
                roadGraphVersion = c.roadGraphVersion;
                vehicles = c.vehicles;
                ordersPerHour = c.ordersPerHour;
                horizonMinutes = c.horizonMinutes;
                repeats = c.repeats;
                seed = c.seed;
                minAssignableSoc = c.minAssignableSoc;
                returnToChargeSoc = c.returnToChargeSoc;
                chargeCompleteSoc = c.chargeCompleteSoc;
                chargeTiming = c.chargeTiming;
                matchStrategy = c.matchStrategy;
                matchWindowTicks = c.matchWindowTicks;
                pressureWindowTicks = c.pressureWindowTicks;
                pressureDeferOrders = c.pressureDeferOrders;
                fullSoc = c.fullSoc;
                busyDrainMetersPerPercent = c.busyDrainMetersPerPercent;
                detourFactor = c.detourFactor;
                avgSpeedKmh = c.avgSpeedKmh;
                serviceSeconds = c.serviceSeconds;
                chargeSeconds = c.chargeSeconds;
                chargeSlots = c.chargeSlots;
                parkWidthM = c.parkWidthM;
                parkHeightM = c.parkHeightM;
                demand = c.demand;
                arrival = c.arrival;
                idleRepositionMinutes = c.idleRepositionMinutes;
                repositionSocFloor = c.repositionSocFloor;
                chargeLayout = c.chargeLayout;
                pileChoice = c.pileChoice;
                chargeCurve = c.chargeCurve;
            }
        }

        public void validate() {
            if (vehicles <= 0 || ordersPerHour <= 0 || horizonMinutes <= 0 || repeats <= 0) {
                throw new IllegalArgumentException("场景参数必须为正: " + this);
            }
            if (busyDrainMetersPerPercent < 50D || detourFactor < 1D) {
                throw new IllegalArgumentException("能耗/绕行参数不合理: " + this);
            }
            if (!(returnToChargeSoc < minAssignableSoc && minAssignableSoc < chargeCompleteSoc
                    && chargeCompleteSoc <= fullSoc)) {
                throw new IllegalArgumentException("SOC 阈值必须递增: " + this);
            }
            if (chargeTiming == ChargeTiming.DEFER_UNDER_PRESSURE
                    && (pressureWindowTicks <= 0 || pressureDeferOrders <= 0)) {
                throw new IllegalArgumentException("错峰推迟需要正的窗口与阈值: " + this);
            }
            if (chargeLayout != null && !chargeLayout.isEmpty()) {
                // 布局臂必须与无布局臂**同容量**，否则测出来的差异里混着"多装了桩"这个另一回事
                if (pileChoice == null) {
                    throw new IllegalArgumentException("给了补能布局就必须指定选址策略");
                }
                if (layoutPileCount() != chargeSlots) {
                    throw new IllegalArgumentException("布局各点桩数合计 " + layoutPileCount()
                            + " 必须等于 chargeSlots=" + chargeSlots + "（容量对齐才能比策略）");
                }
                for (ChargePoint p : chargeLayout) {
                    if (p.piles() < 1 || Double.isNaN(p.x()) || Double.isNaN(p.y())) {
                        throw new IllegalArgumentException("补能点位非法: " + p);
                    }
                }
            }
        }
    }

    /**
     * 单次运行的结果。所有比率都对"到达的全部订单"取分母，不悄悄丢样本。
     *
     * @param hindsightDistanceMeters 事后下界里程，**只累计被接下的单**（口径必须与实际里程一致，
     *                                否则落空的单会把下界顶到实际值之上，regret 变成负数）
     * @param chargeBlockedCarMinutes 空闲车想补能却没有空桩的"车·分钟"累计（§1.3 的桩位硬上限信号；
     *                                排队等待是另一个指标，只统计必充那一支）
     */
    public record RunResult(double completionMeanSeconds,
                            double completionP95Seconds,
                            double pickupWaitMeanSeconds,
                            double avgPickupDistanceMeters,
                            int relocations,
                            Map<String, Integer> pickupArrivals,
                            double totalDistanceMeters,
                            double deadheadShare,
                            int chargeSessions,
                            double chargeQueueSeconds,
                            double chargeBlockedCarMinutes,
                            double chargeTravelMetersPerSession,
                            int pileSessionSpread,
                            double candidatesPerDecision,
                            int completed,
                            int arrived,
                            Map<String, Integer> failureReasons,
                            double hindsightDistanceMeters,
                            double regretVsHindsight) {
    }

    /** N 次重复的汇总：均值 + 95% 置信区间（小样本用 t 分位数，不是 1.96）。 */
    public record Summary(String metric, double mean, double low, double high, double stddev, int n) {
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "%-24s %12.2f  [95%% CI %12.2f .. %12.2f]  sd=%8.2f n=%d",
                    metric, mean, low, high, stddev, n);
        }
    }

    private ScenarioBench() {
    }

    // ---------------------------------------------------------------- 运行

    public static RunResult run(Config cfg, long runSeed) {
        cfg.validate();
        Random rng = new Random(runSeed);
        DecisionPolicy policy = new com.fsd.dispatch.core.RulePolicy();
        DecisionWeights weights = new DecisionWeights(1.0D, 0.15D, 80.0D, 0.5D, 30.0D, cfg.fullSoc(),
                DecisionWeights.DEFAULT_PRIORITY_HIGH_FACTOR, DecisionWeights.DEFAULT_PRIORITY_LOW_FACTOR,
                DecisionWeights.DEFAULT_PEAK_SOC_DAMPING, DecisionWeights.DEFAULT_PLUGGED_BONUS_FALLOFF_METRES);

        List<Vehicle> fleet = new ArrayList<>(cfg.vehicles());
        for (int i = 0; i < cfg.vehicles(); i++) {
            // 初始 SOC 铺满 [returnToChargeSoc, fullSoc]：让"必充"和"顺势补"两条路径一开始就都有车可走
            int soc = cfg.returnToChargeSoc()
                    + (int) Math.round((cfg.fullSoc() - cfg.returnToChargeSoc()) * i / (double) Math.max(1, cfg.vehicles() - 1));
            fleet.add(new Vehicle(i, next(rng, 0, cfg.parkWidthM()), next(rng, 0, cfg.parkHeightM()), soc));
        }
        Map<String, Integer> failures = new TreeMap<>();
        List<Double> completionSeconds = new ArrayList<>();
        ChargingPool chargers = new ChargingPool(cfg.chargeLayout(), cfg.chargeSlots());

        double totalDistance = 0D;
        double loadedDistance = 0D;
        double hindsightDistance = 0D;
        double queueWaitSeconds = 0D;
        int arrived = 0;
        int chargeBlockedTicks = 0;
        long decisionAttempts = 0L;
        long candidateEvaluations = 0L;
        java.util.Deque<Integer> recentUnassigned = new java.util.ArrayDeque<>();
        List<Order> backlog = new ArrayList<>();
        Map<String, Integer> pickupCounts = new TreeMap<>();
        double pickupDistanceTotal = 0D;
        int relocations = 0;
        int ticks = (int) Math.ceil(cfg.horizonMinutes() * 60D / TICK_SECONDS);
        double baseArrivalsPerTick = cfg.ordersPerHour() / 3600D * TICK_SECONDS;

        for (int tick = 0; tick < ticks; tick++) {
            final int nowTick = tick;
            for (Vehicle v : fleet) {
                v.releaseIfReady(tick, cfg);
                v.trackIdle(tick);
            }
            double arrivalsPerTick = baseArrivalsPerTick * cfg.arrival().rateAt(tick * TICK_SECONDS / 3600);
            int arriving = poisson(rng, arrivalsPerTick);
            for (int i = 0; i < arriving; i++) {
                Order order = cfg.demand().stationBased()
                        ? stationOrder(cfg.demand(), arrived, tick, rng)
                        : new Order(arrived, tick, next(rng, 0, cfg.parkWidthM()), next(rng, 0, cfg.parkHeightM()),
                                next(rng, 0, cfg.parkWidthM()), next(rng, 0, cfg.parkHeightM()), "");
                backlog.add(order);
                if (!order.pickupStation().isEmpty()) {
                    // 键 = 站点#小时，一次同时喂给"热区在线估计"（按前缀汇总）与 M6 的站点×小时分布
                    pickupCounts.merge(order.pickupStation() + "#" + (tick * TICK_SECONDS / 3600), 1, Integer::sum);
                }
                arrived++;
            }
            // 决策窗口：窗口=1 时来一单派一单（现状）；>1 时攒到窗口边界一起配对。
            // 两个提前冲刷条件是为了不让等待无界增长：末尾一 tick 必冲， backlog 已经多到超过车队运力时也冲。
            boolean flush = tick == ticks - 1
                    || cfg.matchWindowTicks() <= 1
                    || tick % cfg.matchWindowTicks() == 0
                    || backlog.size() >= cfg.vehicles();
            List<Order> pending = flush ? new ArrayList<>(backlog) : List.of();
            if (flush) {
                backlog.clear();
            }

            int unassigned = 0;
            // 撮合。两档策略吃**同一份 tick 快照**（同一批候选与分数），唯一区别是"谁拿哪台车"怎么定：
            //   SEQUENTIAL_GREEDY = 按到达顺序先到先得（现状语义，也是生产的单次派单形状）
            //   HUNGARIAN         = 本 tick 内全局最小代价配对（M5 的批量撮合）
            List<Plan> plans = new ArrayList<>(pending.size());
            for (Order order : pending) {
                List<Vehicle> free = fleet.stream().filter(v -> v.freeAt(nowTick)).toList();
                // 每次派单尝试要评估多少台车 = 决策成本的规模代理（L 档退化曲线的横轴之一，
                // 也是热路径那个 277 ms 的直接来源）。NO_VEHICLE 那一支同样算一次尝试：
                // 它确实跑了"扫一遍空闲车"的工作，只是结果为空。
                decisionAttempts++;
                candidateEvaluations += free.size();
                if (free.isEmpty()) {
                    failures.merge("NO_VEHICLE", 1, Integer::sum);
                    unassigned++;
                    continue;
                }
                Map<Vehicle, RankedCandidate> scored = scoreEligible(order, free, policy, weights, cfg);
                if (scored.isEmpty()) {
                    failures.merge("LOW_SOC", 1, Integer::sum);
                    unassigned++;
                    continue;
                }
                plans.add(new Plan(order, scored));
            }
            Map<Order, Vehicle> pairing = cfg.matchStrategy() == MatchStrategy.HUNGARIAN
                    ? hungarianPairing(plans) : greedyPairing(plans);
            for (Plan plan : plans) {
                Vehicle chosen = pairing.get(plan.order());
                if (chosen == null) {
                    failures.merge("MATCH_LOST", 1, Integer::sum);
                    unassigned++;
                    continue;
                }
                RankedCandidate best = plan.scored().get(chosen);
                Order order = plan.order();
                double toPickup = best.distanceScore();
                pickupDistanceTotal += toPickup;
                double loaded = roadMeters(order.pickupX, order.pickupY, order.dropoffX, order.dropoffY, cfg);
                double driveMeters = toPickup + loaded;
                // 事后下界只累计"真正被接下的单"：基线 = 全车队（含繁忙车、不看 SOC 与占用）里离取货点最近的
                // 直线接驾 + 同样的载货段。必须在这里、移动车位之前算。
                hindsightDistance += nearestPickupLowerBound(fleet, order, cfg) + loaded;
                double driveSeconds = driveMeters / (cfg.avgSpeedKmh() / 3.6D);
                chosen.startTask(tick, driveSeconds + cfg.serviceSeconds(),
                        metersToSoc(driveMeters, cfg), order.dropoffX, order.dropoffY);
                totalDistance += driveMeters;
                loadedDistance += loaded;
                // 端到端口径：等派时间必须算进完成时长，否则"攒窗口再配对"看起来是免费的。
                double waitSeconds = (tick - order.arrivalTick()) * (double) TICK_SECONDS;
                queueWaitSeconds += waitSeconds;
                completionSeconds.add(waitSeconds + driveSeconds + cfg.serviceSeconds());
            }

            // 补能时机。阈值全部对齐 FleetEnergyProperties，策略分支对齐 EnergyForecastServiceImpl：
            //   必充 = SOC < returnToChargeThreshold(20)，或本 tick 有落空的单而这台车已低到派不出去（留着也没用）；
            //   顺势补 = 策略允许且本 tick 没有落空的单且 SOC < chargeCompleteSoc(90)，只抢当前空位、不排队；
            //   错峰推迟 = 近 pressureWindowTicks 个 tick 的落单数达到 pressureDeferOrders 时，连顺势补也推迟
            //             （对应 shouldDeferReturnToCharge 的"高压不补、但低于安全地板照补"）。
            recentUnassigned.addLast(unassigned);
            while (recentUnassigned.size() > Math.max(1, cfg.pressureWindowTicks())) {
                recentUnassigned.removeFirst();
            }
            long windowUnassigned = recentUnassigned.stream().mapToLong(Integer::longValue).sum();
            boolean peakNow = cfg.arrival().rateAt(tick * TICK_SECONDS / 3600) > 1.0D;
            boolean allowTopUp = switch (cfg.chargeTiming()) {
                case NEVER -> false;
                case OPPORTUNISTIC -> true;
                case DEFER_UNDER_PRESSURE -> windowUnassigned < cfg.pressureDeferOrders();
                case DEFER_IN_PEAK -> !peakNow;
            };
            List<Vehicle> idleBelowComplete = fleet.stream()
                    .filter(v -> v.freeAt(nowTick) && !v.charging && v.soc < cfg.chargeCompleteSoc())
                    .sorted(java.util.Comparator.comparingInt(v -> v.soc))
                    .toList();
            boolean idleDemand = unassigned == 0;
            for (Vehicle v : idleBelowComplete) {
                int chargeTicks = v.chargeTicks(cfg);
                boolean must = v.soc < cfg.returnToChargeSoc()
                        || (!idleDemand && v.soc < cfg.minAssignableSoc());
                if (!must && (!allowTopUp || !idleDemand)) {
                    continue;
                }
                // must = 必充：没有空位也要排队等桩；顺势补只抢当前空位，全满就记一次"被挡"
                ChargingPool.Choice choice = chargers.reserve(tick, chargeTicks, v.x, v.y, cfg, must);
                if (choice == null) {
                    chargeBlockedTicks++;   // 想顺势补能但 6 根桩全占 —— §1.3 说的桩位硬上限
                    continue;
                }
                totalDistance += choice.travelMeters();   // 开去充电是纯空驶：进分子也进空驶率
                v.beginChargingAt(choice, chargeTicks, tick, cfg, chargers.point(choice.pointIndex()),
                        chargers.positioned());
            }

            // M6 热区预置：空闲太久的车主动挪到"目前接单最多的取货站"。
            // 热区是**在线计数**估出来的（不是把答案写进策略），并且只有站点场景才有站点可用。
            if (cfg.idleRepositionMinutes() > 0 && !pickupCounts.isEmpty()) {
                Station hot = hottestStation(cfg.demand(), pickupCounts);
                for (Vehicle v : fleet) {
                    if (!v.freeAt(tick) || v.charging || v.soc <= cfg.repositionSocFloor()) {
                        continue;
                    }
                    if ((tick - v.freeSinceTick) * TICK_SECONDS < cfg.idleRepositionMinutes() * 60L) {
                        continue;
                    }
                    double move = roadMeters(v.x, v.y, hot.x(), hot.y(), cfg);
                    if (move < 1D) {
                        continue;
                    }
                    v.relocate(move, metersToSoc(move, cfg), hot);
                    v.freeSinceTick = tick;
                    totalDistance += move;          // 预置是纯空驶，计入分子不计分母外的量
                    relocations++;
                }
            }
        }

        completionSeconds.sort(Double::compare);
        double mean = completionSeconds.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double p95 = percentile(completionSeconds, 0.95);
        int completed = completionSeconds.size();
        return new RunResult(mean, p95, completed == 0 ? 0D : queueWaitSeconds / completed,
                completed == 0 ? 0D : pickupDistanceTotal / completed, relocations, new TreeMap<>(pickupCounts),
                totalDistance,
                totalDistance <= 0 ? 0D : 1D - loadedDistance / totalDistance,
                chargers.sessions(), chargers.totalQueueSeconds(),
                chargeBlockedTicks * TICK_SECONDS / 60D,
                chargers.sessions() == 0 ? 0D : chargers.totalTravelMeters() / chargers.sessions(),
                chargers.sessionSpread(),
                decisionAttempts == 0 ? 0D : candidateEvaluations / (double) decisionAttempts,
                completed, arrived,
                new LinkedHashMap<>(failures), hindsightDistance,
                hindsightDistance <= 0 ? 0D : (totalDistance - hindsightDistance) / hindsightDistance);
    }

    /** N 次重复 + 95% 置信区间。同一种子序列，所以两次调用逐数字相同。 */
    public static List<Summary> summarise(Config cfg) {
        List<RunResult> runs = new ArrayList<>(cfg.repeats());
        for (int i = 0; i < cfg.repeats(); i++) {
            runs.add(run(cfg, cfg.seed() * 31 + i));
        }
        List<Summary> out = new ArrayList<>();
        out.add(sum("completed_orders", runs, r -> r.completed()));
        out.add(sum("completion_rate", runs, r -> r.arrived() == 0 ? 0D : r.completed() / (double) r.arrived()));
        out.add(sum("completion_mean_s", runs, r -> r.completionMeanSeconds()));
        out.add(sum("completion_p95_s", runs, r -> r.completionP95Seconds()));
        out.add(sum("pickup_wait_mean_s", runs, RunResult::pickupWaitMeanSeconds));
        out.add(sum("avg_pickup_distance_m", runs, RunResult::avgPickupDistanceMeters));
        out.add(sum("relocations", runs, r -> r.relocations()));
        out.add(sum("total_distance_m", runs, r -> r.totalDistanceMeters()));
        out.add(sum("deadhead_share", runs, r -> r.deadheadShare()));
        out.add(sum("charge_sessions", runs, r -> r.chargeSessions()));
        out.add(sum("charge_queue_s", runs, r -> r.chargeQueueSeconds()));
        out.add(sum("charge_blocked_car_min", runs, r -> r.chargeBlockedCarMinutes()));
        out.add(sum("charge_travel_m_session", runs, r -> r.chargeTravelMetersPerSession()));
        out.add(sum("pile_session_spread", runs, r -> r.pileSessionSpread()));
        out.add(sum("candidates_per_decision", runs, r -> r.candidatesPerDecision()));
        out.add(sum("hindsight_distance_served_m", runs, r -> r.hindsightDistanceMeters()));
        out.add(sum("regret_vs_hindsight", runs, r -> r.regretVsHindsight()));
        return out;
    }

    /**
     * 配对对照：两套配置跑**同一串种子**，逐次相减后再取均值与置信区间。
     *
     * <p>为什么不各算各的 CI 再看是否重叠：同种子下两臂共享到达序列，配对差的方差远小于两臂独立，
     * 不配对会把"确实有效应"误判成"分不出来"。判语规则：差的 95% CI 不跨过 0 才允许说有差异。
     */
    public record PairedSummary(String metric, double armA, double armB, double diff,
                                double low, double high, int n) {

        public boolean distinguishable() {
            return low > 0D || high < 0D;
        }
    }

    /** 两臂必须同 repeats 同 seed，否则不是配对比较。 */
    public static List<PairedSummary> comparePaired(Config a, Config b) {
        if (a.repeats() != b.repeats() || a.seed() != b.seed()) {
            throw new IllegalArgumentException("配对比较要求两臂 repeats/seed 一致: " + a + " vs " + b);
        }
        List<RunResult> ra = new ArrayList<>(a.repeats());
        List<RunResult> rb = new ArrayList<>(b.repeats());
        for (int i = 0; i < a.repeats(); i++) {
            long runSeed = a.seed() * 31 + i;
            ra.add(run(a, runSeed));
            rb.add(run(b, runSeed));
        }
        List<PairedSummary> out = new ArrayList<>();
        out.add(diffOf("completion_rate", ra, rb, r -> r.arrived() == 0 ? 0D : r.completed() / (double) r.arrived()));
        out.add(diffOf("completion_mean_s", ra, rb, RunResult::completionMeanSeconds));
        out.add(diffOf("pickup_wait_mean_s", ra, rb, RunResult::pickupWaitMeanSeconds));
        out.add(diffOf("avg_pickup_distance_m", ra, rb, RunResult::avgPickupDistanceMeters));
        out.add(diffOf("relocations", ra, rb, r -> r.relocations()));
        out.add(diffOf("completed_orders", ra, rb, r -> r.completed()));
        out.add(diffOf("total_distance_m", ra, rb, RunResult::totalDistanceMeters));
        out.add(diffOf("deadhead_share", ra, rb, RunResult::deadheadShare));
        out.add(diffOf("charge_sessions", ra, rb, r -> r.chargeSessions()));
        out.add(diffOf("charge_blocked_car_min", ra, rb, RunResult::chargeBlockedCarMinutes));
        out.add(diffOf("charge_travel_m_session", ra, rb, RunResult::chargeTravelMetersPerSession));
        out.add(diffOf("pile_session_spread", ra, rb, RunResult::pileSessionSpread));
        out.add(diffOf("regret_vs_hindsight", ra, rb, RunResult::regretVsHindsight));
        return out;
    }

    private static PairedSummary diffOf(String name, List<RunResult> a, List<RunResult> b,
                                        java.util.function.ToDoubleFunction<RunResult> f) {
        int n = a.size();
        double[] d = new double[n];
        double meanA = 0D;
        double meanB = 0D;
        for (int i = 0; i < n; i++) {
            double x = f.applyAsDouble(a.get(i));
            double y = f.applyAsDouble(b.get(i));
            meanA += x / n;
            meanB += y / n;
            d[i] = x - y;
        }
        double mean = 0D;
        for (double v : d) {
            mean += v / n;
        }
        double sq = 0D;
        for (double v : d) {
            sq += (v - mean) * (v - mean);
        }
        double sd = n > 1 ? Math.sqrt(sq / (n - 1)) : 0D;
        double half = studentT(n) * sd / Math.sqrt(n);
        return new PairedSummary(name, meanA, meanB, mean, mean - half, mean + half, n);
    }

    /** 对照报告正文：两臂各自均值 + 配对差与判定，判语只在 CI 不跨 0 时给方向。 */
    public static String compareReport(Config a, Config b, String title, String labelA, String labelB,
                                       List<PairedSummary> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append("\n\n");
        sb.append("档位 **").append(a.tier()).append("**；两臂同种子同重复次数（n=").append(a.repeats())
                .append("），差值是**逐次配对相减**后再取均值与 95% 置信区间。\n\n");
        sb.append("- A 臂：").append(labelA).append('\n');
        sb.append("- B 臂：").append(labelB).append("\n\n");
        sb.append("| 指标 | A 均值 | B 均值 | 差 (A-B) | 95% CI | 判定 |\n");
        sb.append("| --- | --- | --- | --- | --- | --- |\n");
        for (PairedSummary s : rows) {
            sb.append(String.format(java.util.Locale.ROOT, "| %s | %.4f | %.4f | %+.4f | %+.4f .. %+.4f | %s |%n",
                    s.metric(), s.armA(), s.armB(), s.diff(), s.low(), s.high(),
                    s.distinguishable() ? (s.diff() > 0 ? "A 高于 B" : "A 低于 B") : "分不出来"));
        }
        sb.append("\n## 判语规则\n\n")
                .append("1. 只有\"95% CI 不跨过 0\"才写方向；跨 0 的指标一律报\"分不出来\"，不允许拿点值大小当结论。\n")
                .append("2. **两臂完成量不同时，绝对量不可直接比**：里程/充电次数随\"多派成的单\"一起涨，")
                .append("A 臂里程高不等于更费 —— 要看的是比率型指标（完成率、空驶率、regret）。\n")
                .append("3. 两臂只差一个开关，其余参数逐字相同；换任何参数都要重跑，不能只改一臂。\n\n");
        sb.append("## 假设声明\n\n");
        for (String s : assumptions()) {
            sb.append("- ").append(s).append('\n');
        }
        return sb.toString();
    }

    /**
     * M6 第 1 条：站点×小时的到单分布。跨重复对每个 (站点, 小时) cell 取均值 / P50 / P90，
     * 供容量规划与热区预置当输入。P90 的含义是「90% 的小时里需求不超过这个值」，按它配运力才不会经常爆。
     */
    public record DemandCell(String station, int hour, double mean, double p50, double p90, int repeats) {
    }

    /** @throws IllegalStateException 随机点场景没有站点维度，做不出这张表（不能拿它当"没有热区"的证据） */
    public static List<DemandCell> demandProfile(Config cfg) {
        if (!cfg.demand().stationBased()) {
            throw new IllegalStateException("demandProfile 需要站点场景：先用 withStationDemand(...)");
        }
        Map<String, List<Double>> cells = new TreeMap<>();
        for (int i = 0; i < cfg.repeats(); i++) {
            RunResult r = run(cfg, cfg.seed() * 31 + i);
            r.pickupArrivals().forEach((key, count) ->
                    cells.computeIfAbsent(key, k -> new ArrayList<>()).add((double) count));
        }
        int hours = (int) Math.ceil(cfg.horizonMinutes() / 60D);
        List<DemandCell> out = new ArrayList<>();
        for (Station s : cfg.demand().pickups()) {
            for (int h = 0; h < hours; h++) {
                String key = s.code() + "#" + h;
                List<Double> v = new ArrayList<>(cells.getOrDefault(key, List.of()));
                if (v.size() < cfg.repeats()) {
                    // 某次重复该 cell 一单没有 -> 计数是 0，不是缺失；补齐才不会把 P50 抬高
                    while (v.size() < cfg.repeats()) {
                        v.add(0D);
                    }
                }
                v.sort(Double::compare);
                double mean = v.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
                out.add(new DemandCell(s.code(), h, mean,
                        percentile(v, 0.5), percentile(v, 0.9), v.size()));
            }
        }
        return out;
    }

    /** 需求画像报告：cell 表 + 热区集中度。 */
    public static String demandReport(Config cfg, List<DemandCell> cells) {
        double total = cells.stream().mapToDouble(DemandCell::mean).sum();
        Map<String, Double> byStation = new TreeMap<>();
        cells.forEach(c -> byStation.merge(c.station(), c.mean(), Double::sum));
        StringBuilder sb = new StringBuilder();
        sb.append("# 站点×小时需求画像（M6 第 1 条）\n\n")
                .append("档位 **").append(cfg.tier()).append("**，")
                .append(cells.isEmpty() ? 0 : cells.get(0).repeats()).append(" 次重复；")
                .append("每格是「该站该小时的到单数」的跨重复统计。P90 才是容量规划该用的口径，均值会低估峰值。\n\n");
        sb.append("| 取货站 | 小时段 | 均值 | P50 | P90 |\n| --- | --- | --- | --- | --- |\n");
        for (DemandCell c : cells) {
            sb.append(String.format(java.util.Locale.ROOT, "| %s | %d-%d | %.2f | %.2f | %.2f |%n",
                    c.station(), c.hour(), c.hour() + 1, c.mean(), c.p50(), c.p90()));
        }
        sb.append("\n## 热区集中度（整个视野合计）\n\n");
        byStation.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                // cell.mean 已经是"每次运行的均值"，跨小时相加仍是每次运行的量，不能再除一次 repeats
                .forEach(e -> sb.append(String.format(java.util.Locale.ROOT, "- %s：**%.1f 单/次运行**（占 %.1f%%）%n",
                        e.getKey(), e.getValue(),
                        total <= 0 ? 0D : 100D * e.getValue() / total)));
        sb.append("\n## 假设声明\n\n");
        for (String a : assumptions()) {
            sb.append("- ").append(a).append('\n');
        }
        sb.append("- 站点集合与坐标来自本地库 ACTIVE 的 PICKUP/DROPOFF（现 2 取 4 送；§1.8 的 4 个新站还没进库）\n")
                .append(String.format(java.util.Locale.ROOT, "- 热区强度是**情景假设**：Zipf 指数 skew=%.2f，不是实测%n",
                        cfg.demand().skew()));
        return sb.toString();
    }

    /**
     * §5 第 3 条要的「派单失败原因分布」：每种原因在 N 次重复里的平均出现次数。
     * 分母固定为重复次数（不是"该原因出现过的运行数"），否则只在一次里爆发的原因会被算高。
     */
    public static Map<String, Double> failureMeans(Config cfg) {
        Map<String, Integer> totals = new TreeMap<>();
        for (int i = 0; i < cfg.repeats(); i++) {
            run(cfg, cfg.seed() * 31 + i).failureReasons()
                    .forEach((reason, count) -> totals.merge(reason, count, Integer::sum));
        }
        Map<String, Double> out = new LinkedHashMap<>();
        totals.forEach((reason, total) -> out.put(reason, total / (double) cfg.repeats()));
        return out;
    }

    public static List<String> assumptions() {
        return List.of(
                "距离 = 直线欧氏距离 × detourFactor。**detourFactor 已从假设转实测**：对扩范围路网最大连通分量"
                        + "的 5486 个可通行点对跑最短路/直线距离，均值 1.481、中位 1.374、P90 1.946"
                        + "（`scripts/geo/osm_to_road_graph.py::detour_factor`，统计写在 seed 头部）。"
                        + "取均值 1.481，§1.1 原假设 1.3 相当于中位数",
                "均速 avgSpeedKmh = 17.84，来自 seed 头部 speed_weighted_avg_kmh（按边长加权的限速均值）。"
                        + "裁掉提取框外的路之后从 16.42 升到 17.84 —— 框外多是低速 service 路。仍是单一常数，不分路段等级",
                "场景范围 1613 × 500 m = **现役** 5 个派单围栏的外接框（§0.1 实测）。M 档目标范围 1.35 km²"
                        + " 要等 §1.8 那 4 个站点落地后才能进库，届时必须重跑本表",
                "服务时长固定 serviceSeconds（取 §0.2 实测装货 210 s + 卸货 264 s 之和 474 s），不含排队与人工干预",
                "充电线性（默认）：一次补能的时长 = 实测 1800 s ×（chargeCompleteSoc 90 − 当前 SOC）/（90 − 20），"
                        + "充到 90% 即恢复派单（三个阈值与 FleetEnergyProperties 同值）。"
                        + "回桩位移**默认不计**（chargeLayout 为空 = 原地瞬间开充，历史基线口径）；"
                        + "给了布局就按路网单程计入空驶与耗电，车落点移到该桩位所在点",
                "充电曲线形状是**情景参数不是拟合**（Config.chargeCurve）：拐点与倍率都没有真车数据支撑"
                        + "（§0.2：仓库里没有真车真充电记录），所以只用它做敏感性扫描并给扰动带，"
                        + "不得称\"充电模型\"。默认 LINEAR 与历史逐字相同",
                "补能点位只有 5 个站址是实测坐标（t_station GCJ 换米）。**库里 6 根桩 CP1..CP6 全挂在 "
                        + "ZJF-CHG-01 上且六个车位坐标逐字相同**，所以\"选近桩\"在现役设施下无从谈起 —— "
                        + "spreadSixOverFiveStations 那一臂是 §1.8 的情景假设（把同样 6 根桩摊到 5 个站址），"
                        + "不是现状；桩位在各点间的 2/1/1/1/1 分配同样是假设",
                "补能时机用三档策略枚举（Config.chargeTiming），语义逐条对齐生产：NEVER = 只在必充阈值以下回桩；"
                        + "OPPORTUNISTIC = 现状 idleChargeWhenNoDemand=true（本 tick 无落空单就顺势补、只抢空桩不排队）；"
                        + "DEFER_UNDER_PRESSURE = 错峰推迟（近 pressureWindowTicks 个 tick 累计落单数达到 "
                        + "pressureDeferOrders 时连顺势补也推迟，但必充档不推迟 —— 同 shouldDeferReturnToCharge 的安全兜底）。"
                        + "注意：真实链路的高峰信号是 t_energy_forecast.pressure_p95 而不是 peak mode（§7.2 实测 peak cron 为 NULL）",
                "不做路口冲突/MAPF：本实验台比较的是选车策略的相对差异，不是通行能力",
                "事后基线只是**下界**：对每一笔被接下的单，取全车队（含繁忙车、不看 SOC 与占用）最近车辆的直线接驾"
                        + "距离 + 同样的载货段。它比实际里程必然偏小，所以 regret 偏乐观，不能当\"最优解\"引用",
                "车辆位置在派单瞬间即改为卸货点。候选车本来就只从空闲车里选，所以这不影响选车距离，"
                        + "只会让事后下界更乐观（已并入上一条）",
                "§5 第 3 条列的\"SOC 抛锚次数\"在本模型里**恒为 0，且是构造性的**：全链路 SOC 前置检查"
                        + "（沿用生产的 canCompleteTaskWithSoc 语义）不允许接一趟跑不完的单，所以\"跑到一半没电\"不可观测。"
                        + "可观测的替代信号是 LOW_SOC 落单次数与\"补能被桩位挡住\"的车·分钟，两者都已导出",
                "所有数字都必须带档位标签（S/M/L）；跨档引用即为违规（§1.3）");
    }

    // ---------------------------------------------------------------- 内部模型

    /** @param pickupStation 取货站编码；随机点场景为空串（M6 的热区统计只认站点场景） */
    private record Order(int id, int arrivalTick, double pickupX, double pickupY,
                         double dropoffX, double dropoffY, String pickupStation) {
        String priority() {
            return "NORMAL";
        }
    }

    private static Order stationOrder(Demand demand, int id, int tick, Random rng) {
        Station pickup = demand.pick(demand.pickups(), demand.skew(), rng);
        List<Station> drops = new ArrayList<>(demand.dropoffs());
        drops.removeIf(s -> s.code().equals(pickup.code()));
        Station dropoff = demand.pick(drops.isEmpty() ? demand.dropoffs() : drops, demand.skew(), rng);
        return new Order(id, tick, pickup.x(), pickup.y(), dropoff.x(), dropoff.y(), pickup.code());
    }

    private static final class Vehicle {
        private final long id;
        private double x;
        private double y;
        private int soc;
        private int busyUntilTick = Integer.MIN_VALUE;
        private boolean charging;
        private boolean wasFree = true;
        private int freeSinceTick;

        Vehicle(long id, double x, double y, int soc) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.soc = soc;
        }

        boolean freeAt(int tick) {
            return tick >= busyUntilTick;
        }

        /** 记录"由忙转闲"的时刻，预置策略按空闲时长触发。必须在 releaseIfReady 之后调用。 */
        void trackIdle(int tick) {
            boolean free = freeAt(tick);
            if (free && !wasFree) {
                freeSinceTick = tick;
            }
            wasFree = free;
        }

        /**
         * 接单。<b>位置在派单瞬间就改成卸货点</b>：候选车本来就只从空闲车里选，所以这对选车距离没有影响，
         * 只会让事后下界更乐观 —— 已经写进"regret 偏乐观"的声明里。
         */
        void startTask(int tick, double durationSeconds, int socUsed, double endX, double endY) {
            busyUntilTick = tick + (int) Math.ceil(durationSeconds / TICK_SECONDS);
            soc = Math.max(0, soc - socUsed);
            x = endX;
            y = endY;
        }

        /**
         * 充到 chargeCompleteSoc 需要的 tick 数。默认（{@link ChargeCurve#LINEAR}）等价于历史口径：
         * 按"最深一次补能实测 1800 s"线性折算；给了曲线则把 SOC 高于拐点的部分按倍率拉长。
         */
        int chargeTicks(Config cfg) {
            return Math.max(1, (int) Math.ceil(cfg.chargeSecondsFor(soc, cfg.chargeCompleteSoc()) / TICK_SECONDS));
        }

        /** 热区预置：整段挪到目标站，耗电按同样口径折算（纯空驶，不产生收入）。 */
        void relocate(double meters, int socUsed, Station target) {
            soc = Math.max(0, soc - socUsed);
            x = target.x();
            y = target.y();
        }

        /** 从 startTick 起占用桩位；排队等待同样占用车辆，所以 busyUntilTick 含等待时间。 */
        void beginCharging(int startTick, int chargeTicks) {
            charging = true;
            busyUntilTick = startTick + chargeTicks;
        }

        /**
         * 开去充电。单程是纯空驶（里程与电都要付），车最终落在桩位所在点；
         * 排队与行驶按"先到桩、再在那儿等"取 max，不是相加 —— 否则会把同一段时间算两次。
         *
         * <p>无位置布局（teleport）下 travelMeters 恒为 0，本方法退化成 {@link #beginCharging}。
         */
        void beginChargingAt(ChargingPool.Choice choice, int chargeTicks, int tick,
                             Config cfg, Config.ChargePoint point, boolean moveToPile) {
            double travelSeconds = choice.travelMeters() / (cfg.avgSpeedKmh() / 3.6D);
            soc = Math.max(0, soc - metersToSoc(choice.travelMeters(), cfg));
            // 无位置布局下 point 的坐标不是物理位置（它就是 0,0），挪过去会把车凭空送到园角——
            // 这一支必须原地不动，否则历史基线就不再是同一个模型了（本轮实测：0.93 -> 0.89）。
            if (moveToPile) {
                x = point.x();
                y = point.y();
            }
            int busyTicks = Math.max(choice.waitTicks(), (int) Math.ceil(travelSeconds / TICK_SECONDS));
            beginCharging(tick + busyTicks, chargeTicks);
        }

        void releaseIfReady(int tick, Config cfg) {
            if (charging && tick >= busyUntilTick) {
                charging = false;
                // 与生产同值：充到 chargeCompleteSoc 就恢复派单，不是充到 100
                soc = cfg.chargeCompleteSoc();
            }
        }
    }

    /**
     * 桩位池，带"在哪充"这个空间维度。
     *
     * <p>布局为空时退化成历史上那个**无位置**的单点池：车在原地瞬间开始充电、行驶里程 0。
     * 这样 §13.10 / §13.12 / §13.13 / §13.14 的既有基线仍可逐位复现 —— 补能行驶这一腿是本轮
     * 才加进模型的，它的代价用一个开关来量化，而不是偷偷改掉所有历史数字。
     *
     * <p>给了布局就按各点坐标算单程距离，并由 {@link Config#pileChoice()} 决定去哪个点。
     * 打分统一用**秒**：行驶秒与排队秒才能相加（把等待折成"等效米"是编出来的量纲）。
     */
    private static final class ChargingPool {

        /** 一次选址结果：去哪个点、要等几个 tick、单程多少米。 */
        record Choice(int pointIndex, int waitTicks, double travelMeters) {
        }

        private final List<Config.ChargePoint> points;
        private final int[][] busyUntil;
        private final int[] sessionsPerPoint;
        private final boolean positioned;
        private double queueSeconds;
        private double travelMeters;

        ChargingPool(List<Config.ChargePoint> layout, int legacySlots) {
            this.positioned = layout != null && !layout.isEmpty();
            this.points = positioned ? List.copyOf(layout)
                    : List.of(new Config.ChargePoint("(no-location pool)", 0D, 0D, Math.max(1, legacySlots), false));
            this.busyUntil = new int[points.size()][];
            this.sessionsPerPoint = new int[points.size()];
            for (int i = 0; i < points.size(); i++) {
                busyUntil[i] = new int[Math.max(1, points.get(i).piles())];
            }
        }

        Config.ChargePoint point(int index) {
            return points.get(index);
        }

        /** 这个池子有没有真实空间结构（false = 历史 teleport 口径，车不该被挪到任何坐标）。 */
        boolean positioned() {
            return positioned;
        }

        /** 当前位置到某个点的单程路网距离；无位置布局恒为 0。 */
        double travelMetersTo(int pointIndex, double fromX, double fromY, Config cfg) {
            Config.ChargePoint p = points.get(pointIndex);
            return positioned ? roadMeters(fromX, fromY, p.x(), p.y(), cfg) : 0D;
        }

        /** 该点最早有空位的 tick（所有桩都忙时返回最接近释放的那个）。 */
        int earliestFreeTick(int pointIndex) {
            int best = busyUntil[pointIndex][0];
            for (int busy : busyUntil[pointIndex]) {
                best = Math.min(best, busy);
            }
            return best;
        }

        /**
         * 按策略挑一个点并占位。占位在**决策瞬间**就登记，所以同一 tick 里多台车不会抢同一个空位。
         *
         * @param allowQueue false = 只接受"现在就位"，全满则返回 {@code null}（顺势补能用它，
         *                   避免把桩位预留给不紧急的补能 —— 与历史 `freeAt` 判据同义）
         */
        Choice reserve(int tick, int chargeTicks, double fromX, double fromY,
                       Config cfg, boolean allowQueue) {
            int bestIndex = -1;
            double bestScore = Double.MAX_VALUE;
            int bestWait = 0;
            double bestTravel = 0D;
            for (int i = 0; i < points.size(); i++) {
                int wait = Math.max(0, earliestFreeTick(i) - tick);
                if (!allowQueue && wait > 0) {
                    continue;
                }
                double travel = travelMetersTo(i, fromX, fromY, cfg);
                double travelSeconds = travel / (cfg.avgSpeedKmh() / 3.6D);
                double score = switch (cfg.pileChoice()) {
                    case NEAREST_FREE -> travelSeconds;
                    case NEAREST_INCL_QUEUE -> travelSeconds + wait * TICK_SECONDS;
                    case LEAST_LOADED -> sessionsPerPoint[i] * 1_000_000D + travelSeconds;
                };
                if (score < bestScore) {
                    bestScore = score;
                    bestIndex = i;
                    bestWait = wait;
                    bestTravel = travel;
                }
            }
            if (bestIndex < 0) {
                return null;
            }
            int start = tick + bestWait;
            int slot = argMinBusy(busyUntil[bestIndex]);
            queueSeconds += bestWait * TICK_SECONDS;
            busyUntil[bestIndex][slot] = start + chargeTicks;
            sessionsPerPoint[bestIndex]++;
            travelMeters += bestTravel;
            return new Choice(bestIndex, bestWait, bestTravel);
        }

        private static int argMinBusy(int[] slots) {
            int best = 0;
            for (int i = 1; i < slots.length; i++) {
                if (slots[i] < slots[best]) {
                    best = i;
                }
            }
            return best;
        }

        double totalQueueSeconds() {
            return queueSeconds;
        }

        double totalTravelMeters() {
            return travelMeters;
        }

        /** 各点会话数的极差：选桩分散度的直接读数，0 = 完全摊平（或只有一个点）。 */
        int sessionSpread() {
            int max = sessionsPerPoint[0];
            int min = sessionsPerPoint[0];
            for (int s : sessionsPerPoint) {
                max = Math.max(max, s);
                min = Math.min(min, s);
            }
            return max - min;
        }

        int sessions() {
            return java.util.Arrays.stream(sessionsPerPoint).sum();
        }
    }

    // ---------------------------------------------------------------- 撮合

    /** 一单在本 tick 快照下的可行候选及其分数（分数越低越优，与 {@code RulePolicy} 一致）。 */
    private record Plan(Order order, Map<Vehicle, RankedCandidate> scored) {
    }

    private static Map<Vehicle, RankedCandidate> scoreEligible(Order order, List<Vehicle> free,
                                                               DecisionPolicy policy, DecisionWeights weights,
                                                               Config cfg) {
        List<DecisionInput.CandidateState> states = new ArrayList<>(free.size());
        for (Vehicle v : free) {
            if (v.soc < cfg.minAssignableSoc()) {
                continue;
            }
            double toPickup = roadMeters(v.x, v.y, order.pickupX, order.pickupY, cfg);
            double task = toPickup + roadMeters(order.pickupX, order.pickupY, order.dropoffX, order.dropoffY, cfg)
                    + 200D; // 回桩预留，与 DispatchVehicleAssignServiceImpl 的 200 m 兜底一致
            if (v.soc - metersToSoc(task, cfg) < cfg.minAssignableSoc()) {
                continue;
            }
            states.add(new DecisionInput.CandidateState(
                    new DecisionInput.RankedCandidateIdentity(v.id, "SIM-" + v.id),
                    v.soc, toPickup, false, 0L));
        }
        if (states.isEmpty()) {
            return Map.of();
        }
        DecisionOutcome outcome = policy.decide(new DecisionInput(
                order.priority(), false, 1.0D, weights, states));
        Map<Vehicle, RankedCandidate> byVehicle = new LinkedHashMap<>();
        for (RankedCandidate ranked : outcome.ranked()) {
            free.stream().filter(v -> v.id == ranked.identity().vehicleId()).findFirst()
                    .ifPresent(v -> byVehicle.put(v, ranked));
        }
        return byVehicle;
    }

    /** 先到先得：按到达顺序，各单拿自己还没被抢走的最优车。 */
    private static Map<Order, Vehicle> greedyPairing(List<Plan> plans) {
        Map<Order, Vehicle> pair = new LinkedHashMap<>();
        Set<Vehicle> taken = new HashSet<>();
        for (Plan plan : plans) {
            Vehicle best = null;
            double bestScore = Double.MAX_VALUE;
            for (Map.Entry<Vehicle, RankedCandidate> e : plan.scored().entrySet()) {
                if (!taken.contains(e.getKey()) && e.getValue().totalScore() < bestScore) {
                    best = e.getKey();
                    bestScore = e.getValue().totalScore();
                }
            }
            if (best != null) {
                taken.add(best);
                pair.put(plan.order(), best);
            }
        }
        return pair;
    }

    /** 本 tick 全局最小总分数配对（代价 = 生产同一份 {@code RulePolicy} 的总分，不可行对用 BIG 挡掉）。 */
    private static Map<Order, Vehicle> hungarianPairing(List<Plan> plans) {
        List<Vehicle> vehicles = new ArrayList<>();
        for (Plan plan : plans) {
            for (Vehicle v : plan.scored().keySet()) {
                if (!vehicles.contains(v)) {
                    vehicles.add(v);
                }
            }
        }
        int rows = plans.size();
        int cols = vehicles.size();
        if (rows == 0 || cols == 0) {
            return Map.of();
        }
        boolean transposed = rows > cols;
        double[][] cost = new double[Math.min(rows, cols)][Math.max(rows, cols)];
        for (int i = 0; i < cost.length; i++) {
            java.util.Arrays.fill(cost[i], UNPAIRABLE);
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                RankedCandidate ranked = plans.get(r).scored().get(vehicles.get(c));
                double value = ranked == null ? UNPAIRABLE : ranked.totalScore();
                if (transposed) {
                    cost[c][r] = value;
                } else {
                    cost[r][c] = value;
                }
            }
        }
        int[] rowToCol = hungarian(cost);
        Map<Order, Vehicle> pair = new LinkedHashMap<>();
        Set<Vehicle> used = new HashSet<>();
        for (int r = 0; r < cost.length; r++) {
            int c = rowToCol[r];
            int orderIdx = transposed ? c : r;
            int vehicleIdx = transposed ? r : c;
            if (c < 0 || orderIdx >= rows || vehicleIdx >= cols) {
                continue;
            }
            if (cost[r][c] >= UNPAIRABLE) {
                continue;
            }
            Vehicle v = vehicles.get(vehicleIdx);
            if (used.add(v)) {
                pair.put(plans.get(orderIdx).order(), v);
            }
        }
        return pair;
    }

    private static final double UNPAIRABLE = 1.0E12D;

    /**
     * 矩形指派问题的匈牙利算法（n 行 &lt;= m 列，O(n^2 * m)）。返回每行分到的列下标。
     *
     * <p>实现取自 e-maxx 的势函数版本：不可行对用 {@link #UNPAIRABLE} 这种有限大数挡，而不是无限 ——
     * 用无限会让"行多列少"时的可行度判定失真，也拿不到"这行根本没配上"的信号。
     */
    static int[] hungarian(double[][] cost) {
        int n = cost.length;
        int m = cost[0].length;
        double[] u = new double[n + 1];
        double[] v = new double[m + 1];
        int[] p = new int[m + 1];
        int[] way = new int[m + 1];
        java.util.Arrays.fill(p, 0);
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[m + 1];
            boolean[] used = new boolean[m + 1];
            java.util.Arrays.fill(minv, Double.POSITIVE_INFINITY);
            do {
                used[j0] = true;
                int i0 = p[j0];
                int j1 = -1;
                double delta = Double.POSITIVE_INFINITY;
                for (int j = 1; j <= m; j++) {
                    if (used[j]) {
                        continue;
                    }
                    double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                    if (cur < minv[j]) {
                        minv[j] = cur;
                        way[j] = j0;
                    }
                    if (minv[j] < delta) {
                        delta = minv[j];
                        j1 = j;
                    }
                }
                if (j1 < 0) {
                    break;
                }
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] answer = new int[n];
        for (int j = 1; j <= m; j++) {
            if (p[j] != 0) {
                answer[p[j] - 1] = j - 1;
            }
        }
        return answer;
    }

    // ---------------------------------------------------------------- 统计工具

    private static double next(Random rng, double from, double to) {
        return from + rng.nextDouble() * Math.max(0D, to - from);
    }

    private static int poisson(Random rng, double lambda) {
        if (lambda <= 0) {
            return 0;
        }
        // Knuth 采样；lambda 在 1 上下，不需要正态近似
        double limit = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            p *= rng.nextDouble();
        } while (p > limit && ++k < 64);
        return k;
    }

    private static double roadMeters(double x1, double y1, double x2, double y2, Config cfg) {
        return Math.hypot(x2 - x1, y2 - y1) * cfg.detourFactor();
    }

    /** 在线估热区：取"到目前为止接单最多"的取货站；并列按编码取小，保证可复现。 */
    private static Station hottestStation(Demand demand, Map<String, Integer> counts) {
        Station best = demand.pickups().get(0);
        int bestCount = -1;
        for (Station s : demand.pickups()) {
            int c = counts.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(s.code() + "#"))
                    .mapToInt(Map.Entry::getValue).sum();
            if (c > bestCount || (c == bestCount && s.code().compareTo(best.code()) < 0)) {
                best = s;
                bestCount = c;
            }
        }
        return best;
    }

    private static int metersToSoc(double meters, Config cfg) {
        return (int) Math.ceil(meters / Math.max(50D, cfg.busyDrainMetersPerPercent()));
    }

    /**
     * 单笔订单的接驾里程下界：全车队（含繁忙车）里离取货点最近的直线距离 × 绕行系数。
     * 只用于"这一单如果派给最优的车能省多少"，不看 SOC 与占用，所以是下界而不是可行解。
     */
    private static double nearestPickupLowerBound(List<Vehicle> fleet, Order order, Config cfg) {
        double best = Double.MAX_VALUE;
        for (Vehicle v : fleet) {
            best = Math.min(best, roadMeters(v.x, v.y, order.pickupX, order.pickupY, cfg));
        }
        return best == Double.MAX_VALUE ? 0D : best;
    }

    private static double percentile(List<Double> sorted, double q) {
        if (sorted.isEmpty()) {
            return 0D;
        }
        int idx = (int) Math.min(sorted.size() - 1L, Math.ceil(q * sorted.size()) - 1);
        return sorted.get(Math.max(0, idx));
    }

    private static Summary sum(String name, List<RunResult> runs, java.util.function.ToDoubleFunction<RunResult> f) {
        double[] v = runs.stream().mapToDouble(f).toArray();
        double mean = java.util.Arrays.stream(v).average().orElse(0D);
        double sd = 0D;
        if (v.length > 1) {
            double sq = 0D;
            for (double x : v) {
                sq += (x - mean) * (x - mean);
            }
            sd = Math.sqrt(sq / (v.length - 1));
        }
        double half = studentT(v.length) * sd / Math.sqrt(v.length);
        return new Summary(name, mean, mean - half, mean + half, sd, v.length);
    }

    /**
     * 双侧 95% 的 t 分位数小表，**自变量是样本数 n、取 df = n-1**；n>=30 用正态近似 1.96。
     *
     * <p>2026-09-22 修：这张表在 n≥6 的整段一直写成了 t(df=n)（如 n=12 给 2.179 而 t(df=11)=2.201），
     * 于是区间系统性偏窄 1%–5%。方向上是反保守的 —— 只会让"分不出来"的东西被说成"分得出来"。
     * 已记录结论的分离幅度都远大于这个比例，判定未受影响（见 §13.22）。
     */
    private static final Map<Integer, Double> T_975 = Map.ofEntries(
            Map.entry(2, 12.706), Map.entry(3, 4.303), Map.entry(4, 3.183), Map.entry(5, 2.776),
            Map.entry(6, 2.571), Map.entry(7, 2.447), Map.entry(8, 2.365), Map.entry(9, 2.306),
            Map.entry(10, 2.262), Map.entry(11, 2.228), Map.entry(12, 2.201), Map.entry(13, 2.179),
            Map.entry(14, 2.160), Map.entry(15, 2.145), Map.entry(16, 2.131), Map.entry(17, 2.120),
            Map.entry(18, 2.110), Map.entry(19, 2.101), Map.entry(20, 2.093), Map.entry(25, 2.064),
            Map.entry(30, 2.045));

    static double studentT(int n) {
        if (n <= 1) {
            return 0D;
        }
        Double exact = T_975.get(n);
        if (exact != null) {
            return exact;
        }
        if (n >= 30) {
            return 1.96D;
        }
        // 表里没有的 n（21–24 / 26–29）退回**不超过它的最大档**：df 更小 ⇒ t 更大 ⇒ 区间只会偏宽不会偏窄。
        int floorKey = 2;
        for (int key : T_975.keySet()) {
            if (key < n && key > floorKey) {
                floorKey = key;
            }
        }
        return T_975.get(floorKey);
    }

    /** 报告正文；调用方负责落盘。 */
    public static String report(Config cfg, List<Summary> summaries) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 仿真实验台报告\n\n");
        sb.append("档位 **").append(cfg.tier()).append("** —— 本节任何数字都不得与其他档混用（§1.3）。\n\n");
        sb.append("## 场景配置\n\n```\n").append(cfg).append("\n```\n\n");
        sb.append("## 指标（").append(cfg.repeats()).append(" 次重复，95% 置信区间）\n\n");
        sb.append("| 指标 | 均值 | 95% CI | 标准差 | n |\n| --- | --- | --- | --- | --- |\n");
        for (Summary s : summaries) {
            sb.append(String.format(java.util.Locale.ROOT, "| %s | %.2f | %.2f .. %.2f | %.2f | %d |%n",
                    s.metric(), s.mean(), s.low(), s.high(), s.stddev(), s.n()));
        }
        sb.append("\n## 派单失败原因分布（每次运行的平均次数，§5 第 3 条）\n\n");
        Map<String, Double> fails = failureMeans(cfg);
        if (fails.isEmpty()) {
            sb.append("（本场景没有落空的单 —— 压力不够，不能用来谈运力）\n");
        } else {
            sb.append("| 原因 | 平均次数/次运行 |\n| --- | --- |\n");
            fails.forEach((reason, meanCount) -> sb.append(String.format(java.util.Locale.ROOT,
                    "| %s | %.2f |%n", reason, meanCount)));
        }
        sb.append("\n## 假设声明（§5 要求随结果一起给出）\n\n");
        for (String a : assumptions()) {
            sb.append("- ").append(a).append('\n');
        }
        return sb.toString();
    }
}

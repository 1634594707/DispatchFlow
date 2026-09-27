package com.fsd.dispatch.sim;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P0-1 读库 replay（决策级审计，面试路线图）：JDBC 只读直连，对 {@code t_dispatch_decision_snapshot}
 * 的每条历史决策做三件事：
 * <ol>
 *   <li><b>漏斗统计</b>：candidateTotal → freshTelemetry → socEligible → socChain → reachable →
 *       evaluated，逐级均值与保留率——"没有车可派"发生在哪一级，从这里读；</li>
 *   <li><b>一致性审计</b>：winner 是否真是候选里总分最低（argmin）、scoreGap 是否等于 runnerUp−winner、
 *       tieCount 是否与 ε 内并列数一致——快照是"为什么选这台车"的证据，证据自洽才谈得上回放；</li>
 *   <li><b>影子面</b>：shadowAgreed 率与 shadowRegret 均值/最大（有影子记录的行）。</li>
 * </ol>
 *
 * <p>上下文统计（表存在才统计，缺表降级 N/A）：{@code t_order} 状态分布、{@code t_vehicle} 状态分布、
 * {@code t_fleet_telemetry_point} 行数——满足"读取订单、车辆状态、决策快照和遥测"的口径。
 *
 * <p><b>边界（2026-09-27 裁定）</b>：{@code candidatesJson} 只存 top-5 分量（distance/socMargin/
 * bonus/total），没有原始入参（soc/路网距离/压力/引力视图），所以本审计做<b>分量级一致性</b>，
 * 不做反事实重派——反事实 replay 需要 Flyway 给快照补原始入参字段，那是口径变更，未裁不动。
 *
 * <p>用法（对真实库；纯只读，不写任何表）：
 * <pre>
 *   java -cp back/fsd-dispatch/target/classes com.fsd.dispatch.sim.DecisionReplay \
 *     --url jdbc:mysql://127.0.0.1:3306/fsd_core --user root --password xxx \
 *     [--limit 2000] [--out reports/experiments/decision-replay.md]
 * </pre>
 * 退出码恒 0；不一致条目在报告里逐条列出。
 */
public final class DecisionReplay {

    /** 快照内候选分量的 JSON 结构与 {@code DispatchDecisionSnapshotServiceImpl#writeCandidates} 对齐。 */
    public record CandidateComponent(long vehicleId, String vehicleCode, double total) {
    }

    public record SnapshotRow(long id, Long orderId, String orderNo, String policyId, String policyVersion,
                              String shadowPolicyId, Integer shadowAgreed, Double shadowRegret,
                              Integer candidateTotal, Integer freshTelemetry, Integer socEligible,
                              Integer socChainEligible, Integer reachable, Integer evaluated,
                              String candidatesJson, Long winnerVehicleId, String winnerVehicleCode,
                              Double winnerScore, Double runnerUpScore, Double scoreGap,
                              Integer tieCount, String failReason, Timestamp generatedAt) {
    }

    /** 单条一致性结论：null 表示该维度无信号（如失败单没有 winner），Boolean 表示自洽与否。 */
    public record AuditRow(long id, boolean candidateJsonOk, Boolean winnerIsArgmin,
                           Boolean scoreGapConsistent, Boolean tieCountConsistent, String note) {
    }

    public record AuditResult(int snapshots,
                              int candidateJsonErrors,
                              int consistencyViolations,
                              double shadowAgreedRate,
                              int shadowRows,
                              double shadowRegretMean,
                              double shadowRegretMax,
                              Map<String, Double> funnelMeans,
                              Map<String, Long> orderStatusCounts,
                              Map<String, Long> vehicleStatusCounts,
                              Long telemetryPoints,
                              List<AuditRow> rows) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final double MONEY_EPSILON = 1e-4D;
    private static final double TIE_EPSILON = 1e-9D;

    private DecisionReplay() {
    }

    public static List<SnapshotRow> readSnapshots(Connection connection, int limit) throws SQLException {
        String sql = "SELECT id, order_id, order_no, policy_id, policy_version, shadow_policy_id, "
                + "shadow_agreed, shadow_regret, candidate_total, fresh_telemetry_count, "
                + "soc_eligible_count, soc_chain_eligible_count, reachable_count, candidate_evaluated, "
                + "candidates_json, winner_vehicle_id, winner_vehicle_code, winner_score, "
                + "runner_up_score, score_gap, tie_count, fail_reason, generated_at "
                + "FROM t_dispatch_decision_snapshot WHERE deleted = 0 ORDER BY id"
                + (limit > 0 ? " LIMIT " + Math.max(1, limit) : "");
        List<SnapshotRow> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new SnapshotRow(
                        rs.getLong("id"),
                        (Long) rs.getObject("order_id"),
                        rs.getString("order_no"),
                        rs.getString("policy_id"),
                        rs.getString("policy_version"),
                        rs.getString("shadow_policy_id"),
                        (Integer) rs.getObject("shadow_agreed"),
                        rs.getObject("shadow_regret") == null ? null : rs.getDouble("shadow_regret"),
                        (Integer) rs.getObject("candidate_total"),
                        (Integer) rs.getObject("fresh_telemetry_count"),
                        (Integer) rs.getObject("soc_eligible_count"),
                        (Integer) rs.getObject("soc_chain_eligible_count"),
                        (Integer) rs.getObject("reachable_count"),
                        (Integer) rs.getObject("candidate_evaluated"),
                        rs.getString("candidates_json"),
                        (Long) rs.getObject("winner_vehicle_id"),
                        rs.getString("winner_vehicle_code"),
                        rs.getObject("winner_score") == null ? null : rs.getDouble("winner_score"),
                        rs.getObject("runner_up_score") == null ? null : rs.getDouble("runner_up_score"),
                        rs.getObject("score_gap") == null ? null : rs.getDouble("score_gap"),
                        (Integer) rs.getObject("tie_count"),
                        rs.getString("fail_reason"),
                        rs.getTimestamp("generated_at")));
            }
        }
        return rows;
    }

    public static List<CandidateComponent> parseCandidates(String candidatesJson) throws IOException {
        if (candidatesJson == null || candidatesJson.isBlank()) {
            return List.of();
        }
        List<CandidateComponent> out = new ArrayList<>();
        JsonNode array = MAPPER.readTree(candidatesJson);
        if (!array.isArray()) {
            throw new IOException("candidatesJson 不是数组: " + candidatesJson);
        }
        for (JsonNode node : array) {
            out.add(new CandidateComponent(
                    node.path("vehicleId").asLong(),
                    node.path("vehicleCode").asText(null),
                    node.path("total").asDouble()));
        }
        return out;
    }

    /** 单条审计：JSON 可解析性 + winner=argmin + scoreGap/tieCount 自洽。失败单（无 winner）只审 JSON。 */
    public static AuditRow audit(SnapshotRow row) {
        boolean jsonOk = true;
        Boolean winnerIsArgmin = null;
        Boolean scoreGapOk = null;
        Boolean tieOk = null;
        String note = row.failReason() == null ? "" : "failReason=" + row.failReason();
        List<CandidateComponent> candidates;
        try {
            candidates = parseCandidates(row.candidatesJson());
        } catch (IOException ex) {
            return new AuditRow(row.id(), false, null, null, null, "candidatesJson 不可解析: " + ex.getMessage());
        }
        if (row.winnerVehicleId() == null) {
            return new AuditRow(row.id(), jsonOk, null, null, null, note);
        }
        if (!candidates.isEmpty()) {
            double best = candidates.stream().mapToDouble(CandidateComponent::total).min().orElse(Double.NaN);
            CandidateComponent winner = candidates.stream()
                    .filter(c -> c.vehicleId() == row.winnerVehicleId())
                    .findFirst().orElse(null);
            if (winner == null) {
                // top-5 截断：winner 不在留存清单里（MAX_CANDIDATES_KEPT），不算违规，记一条说明
                note = (note.isEmpty() ? "" : note + "; ") + "winner 不在 top-5 快照内（截断语义）";
            } else {
                winnerIsArgmin = Math.abs(winner.total() - best) <= MONEY_EPSILON;
            }
        }
        if (row.scoreGap() != null && row.runnerUpScore() != null && row.winnerScore() != null) {
            scoreGapOk = Math.abs(row.scoreGap() - (row.runnerUpScore() - row.winnerScore())) <= MONEY_EPSILON;
        }
        if (row.tieCount() != null && !candidates.isEmpty()) {
            int ties = 0;
            for (CandidateComponent candidate : candidates) {
                if (Math.abs(candidate.total() - row.winnerScore()) <= TIE_EPSILON) {
                    ties++;
                }
            }
            tieOk = ties == row.tieCount();
        }
        return new AuditRow(row.id(), jsonOk, winnerIsArgmin, scoreGapOk, tieOk, note);
    }

    public static AuditResult auditAll(List<SnapshotRow> snapshots, Connection connection) {
        int jsonErrors = 0;
        int violations = 0;
        int shadowRows = 0;
        int shadowAgreed = 0;
        double regretSum = 0D;
        double regretMax = 0D;
        List<AuditRow> auditRows = new ArrayList<>(snapshots.size());
        Map<String, List<Integer>> funnel = new LinkedHashMap<>();
        for (String key : List.of("candidate_total", "fresh_telemetry", "soc_eligible",
                "soc_chain_eligible", "reachable", "evaluated")) {
            funnel.put(key, new ArrayList<>());
        }
        for (SnapshotRow row : snapshots) {
            AuditRow audit = audit(row);
            auditRows.add(audit);
            if (!audit.candidateJsonOk()) {
                jsonErrors++;
            }
            if (isFalse(audit.winnerIsArgmin()) || isFalse(audit.scoreGapConsistent())
                    || isFalse(audit.tieCountConsistent())) {
                violations++;
            }
            if (row.shadowAgreed() != null) {
                shadowRows++;
                if (row.shadowAgreed() == 1) {
                    shadowAgreed++;
                }
            }
            if (row.shadowRegret() != null) {
                regretSum += row.shadowRegret();
                regretMax = Math.max(regretMax, row.shadowRegret());
            }
            funnel.get("candidate_total").add(row.candidateTotal());
            funnel.get("fresh_telemetry").add(row.freshTelemetry());
            funnel.get("soc_eligible").add(row.socEligible());
            funnel.get("soc_chain_eligible").add(row.socChainEligible());
            funnel.get("reachable").add(row.reachable());
            funnel.get("evaluated").add(row.evaluated());
        }
        Map<String, Double> funnelMeans = new LinkedHashMap<>();
        funnel.forEach((key, values) -> funnelMeans.put(key, values.stream().filter(
                v -> v != null).mapToInt(Integer::intValue).average().orElse(Double.NaN)));
        return new AuditResult(snapshots.size(), jsonErrors, violations,
                shadowRows == 0 ? Double.NaN : shadowAgreed / (double) shadowRows,
                shadowRows,
                shadowRows == 0 ? 0D : regretSum / shadowRows,
                regretMax,
                funnelMeans,
                orderStatusCounts(connection),
                vehicleStatusCounts(connection),
                telemetryCount(connection),
                auditRows);
    }

    private static boolean isFalse(Boolean value) {
        return value != null && !value;
    }

    /** 表存在才统计，缺表/缺列降级为空 Map——审计的上下文部分不允许反过来弄挂主流程。 */
    public static Map<String, Long> orderStatusCounts(Connection connection) {
        return groupStatus(connection,
                "SELECT status, COUNT(*) FROM t_order WHERE deleted = 0 GROUP BY status");
    }

    public static Map<String, Long> vehicleStatusCounts(Connection connection) {
        return groupStatus(connection,
                "SELECT status, COUNT(*) FROM t_vehicle WHERE deleted = 0 GROUP BY status");
    }

    private static Map<String, Long> groupStatus(Connection connection, String sql) {
        Map<String, Long> out = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                out.put(String.valueOf(rs.getObject(1)), rs.getLong(2));
            }
        } catch (SQLException ex) {
            return Map.of();
        }
        return out;
    }

    public static Long telemetryCount(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM t_fleet_telemetry_point");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException ex) {
            return null;
        }
    }

    public static String report(AuditResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 决策快照回放审计（P0-1 读库 replay）\n\n");
        sb.append("- 快照行数：").append(result.snapshots())
                .append("；JSON 不可解析：").append(result.candidateJsonErrors())
                .append("；一致性违规：**").append(result.consistencyViolations()).append("**\n");
        sb.append("- 影子面：有影子记录 ").append(result.shadowRows()).append(" 行，一致率 ")
                .append(Double.isNaN(result.shadowAgreedRate()) ? "N/A" :
                        String.format(java.util.Locale.ROOT, "%.4f", result.shadowAgreedRate()))
                .append("，regret 均值 ").append(String.format(java.util.Locale.ROOT, "%.4f", result.shadowRegretMean()))
                .append("，最大 ").append(String.format(java.util.Locale.ROOT, "%.4f", result.shadowRegretMax()))
                .append("\n\n");
        sb.append("## 候选漏斗（均值）\n\n| 级 | 均值 |\n| --- | --- |\n");
        result.funnelMeans().forEach((key, value) -> sb.append("| ").append(key).append(" | ")
                .append(Double.isNaN(value) ? "N/A" : String.format(java.util.Locale.ROOT, "%.2f", value))
                .append(" |\n"));
        sb.append("\n## 上下文（表存在才统计）\n\n");
        sb.append("- t_order 状态分布：").append(result.orderStatusCounts().isEmpty() ? "N/A" : result.orderStatusCounts()).append('\n');
        sb.append("- t_vehicle 状态分布：").append(result.vehicleStatusCounts().isEmpty() ? "N/A" : result.vehicleStatusCounts()).append('\n');
        sb.append("- t_fleet_telemetry_point 行数：").append(result.telemetryPoints() == null ? "N/A" : result.telemetryPoints()).append("\n\n");
        sb.append("## 一致性明细\n\n");
        long flagged = result.rows().stream()
                .filter(r -> !r.candidateJsonOk() || isFalse(r.winnerIsArgmin())
                        || isFalse(r.scoreGapConsistent()) || isFalse(r.tieCountConsistent()))
                .count();
        sb.append(flagged == 0 ? "全部自洽（无违规行）。\n" : flagged + " 行需要人工复核：\n");
        for (AuditRow row : result.rows()) {
            boolean bad = !row.candidateJsonOk() || isFalse(row.winnerIsArgmin())
                    || isFalse(row.scoreGapConsistent()) || isFalse(row.tieCountConsistent());
            if (bad) {
                sb.append("- id=").append(row.id())
                        .append(" jsonOk=").append(row.candidateJsonOk())
                        .append(" winnerIsArgmin=").append(row.winnerIsArgmin())
                        .append(" scoreGapConsistent=").append(row.scoreGapConsistent())
                        .append(" tieCountConsistent=").append(row.tieCountConsistent())
                        .append(" ").append(row.note()).append('\n');
            }
        }
        sb.append("\n> 边界：candidatesJson 只存 top-5 分量、无原始入参，本审计是分量级一致性，"
                + "不做反事实重派——反事实 replay 需要先给快照补原始入参字段（口径变更，未裁不动）。\n");
        return sb.toString();
    }

    public static void main(String[] args) throws java.io.IOException, SQLException {
        String url = null;
        String user = null;
        String password = null;
        int limit = 2000;
        String out = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--url" -> url = args[++i];
                case "--user" -> user = args[++i];
                case "--password" -> password = args[++i];
                case "--limit" -> limit = Integer.parseInt(args[++i]);
                case "--out" -> out = args[++i];
                default -> throw new IllegalArgumentException("未知参数: " + args[i]);
            }
        }
        if (url == null) {
            throw new IllegalArgumentException("必须提供 --url（如 jdbc:mysql://127.0.0.1:3306/fsd_core）");
        }
        try (Connection connection = java.sql.DriverManager.getConnection(url, user, password)) {
            AuditResult result = auditAll(readSnapshots(connection, limit), connection);
            String markdown = report(result);
            System.out.println(markdown);
            if (out != null) {
                Path path = Path.of(out);
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, markdown, StandardCharsets.UTF_8);
                System.out.println("[OK] 报告已写 " + path.toAbsolutePath());
            }
        }
    }
}

package com.fsd.dispatch.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fsd.dispatch.sim.DecisionReplay.AuditResult;
import com.fsd.dispatch.sim.DecisionReplay.SnapshotRow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P0-1 读库 replay 的闸门测试：H2 内存库放三份快照（一份自洽、一份 scoreGap 被篡改、
 * 一份失败单）+ 订单/车辆/遥测上下文，审计必须找出那 1 条不一致、统计出影子面与漏斗，
 * 且缺表降级不挂。报告要带上"分量级审计、不做反事实"的边界声明。
 */
class DecisionReplayTest {

    private static Connection connection;

    @BeforeAll
    static void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:h2:mem:replay;MODE=MySQL", "sa", "");
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE t_dispatch_decision_snapshot ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "park_id BIGINT, task_id BIGINT, order_id BIGINT, order_no VARCHAR(64),"
                    + "policy_id VARCHAR(32), policy_version VARCHAR(32),"
                    + "shadow_policy_id VARCHAR(32), shadow_policy_version VARCHAR(32),"
                    + "shadow_winner_code VARCHAR(64), shadow_agreed INT, shadow_regret DECIMAL(12,4),"
                    + "profile_id BIGINT, profile_type VARCHAR(32), gray_bucket INT, experiment_side INT,"
                    + "candidate_total INT, fresh_telemetry_count INT, soc_eligible_count INT,"
                    + "soc_chain_eligible_count INT, reachable_count INT, candidate_evaluated INT,"
                    + "candidates_json TEXT, winner_vehicle_id BIGINT, winner_vehicle_code VARCHAR(64),"
                    + "winner_score DECIMAL(12,4), runner_up_score DECIMAL(12,4), score_gap DECIMAL(12,4),"
                    + "tie_count INT, match_algorithm VARCHAR(32), road_graph_version VARCHAR(64),"
                    + "fail_reason VARCHAR(64), duration_micros BIGINT, confidence DECIMAL(8,4),"
                    + "generated_at TIMESTAMP, remark VARCHAR(512), created_at TIMESTAMP,"
                    + "updated_at TIMESTAMP, deleted INT DEFAULT 0)");
            st.execute("CREATE TABLE t_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "status VARCHAR(32), deleted INT DEFAULT 0)");
            st.execute("CREATE TABLE t_vehicle (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "status VARCHAR(32), deleted INT DEFAULT 0)");
            st.execute("CREATE TABLE t_fleet_telemetry_point (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "vehicle_id BIGINT)");
        }
        String candidates = "[{\"vehicleId\":1,\"vehicleCode\":\"SIM-1\",\"distance\":100.0,"
                + "\"socMargin\":1.5,\"pluggedBonus\":0.0,\"idleBonus\":0.0,\"priorityFactor\":1.0,"
                + "\"forecastPenalty\":0.0,\"total\":101.5},"
                + "{\"vehicleId\":2,\"vehicleCode\":\"SIM-2\",\"distance\":108.0,"
                + "\"socMargin\":2.0,\"pluggedBonus\":0.0,\"idleBonus\":0.0,\"priorityFactor\":1.0,"
                + "\"forecastPenalty\":0.0,\"total\":110.0}]";
        // ① 自洽 + 影子记录
        insertSnapshot(candidates, 1L, 101.5D, 110.0D, 8.5D, 1, null, 1, 0.5D);
        // ② scoreGap 被篡改 → 必须被判违规
        insertSnapshot(candidates, 1L, 101.5D, 110.0D, 99.0D, 1, null, null, null);
        // ③ 失败单：无 winner、无候选——只做 JSON 审计，不算违规
        insertSnapshot("[]", null, null, null, null, null, "NO_VEHICLE", null, null);
        // 上下文
        try (Statement st = connection.createStatement()) {
            st.execute("INSERT INTO t_order (status) VALUES ('COMPLETED'),('COMPLETED'),('PENDING')");
            st.execute("INSERT INTO t_vehicle (status) VALUES ('ONLINE'),('ONLINE'),('OFFLINE')");
            st.execute("INSERT INTO t_fleet_telemetry_point (vehicle_id) VALUES (1),(1),(2),(2),(2)");
        }
    }

    private static void insertSnapshot(String candidatesJson, Long winnerId, Double winnerScore,
                                       Double runnerUpScore, Double scoreGap, Integer tieCount,
                                       String failReason, Integer shadowAgreed, Double shadowRegret)
            throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO t_dispatch_decision_snapshot (order_id, order_no, policy_id, policy_version, "
                        + "shadow_policy_id, shadow_agreed, shadow_regret, candidate_total, "
                        + "fresh_telemetry_count, soc_eligible_count, soc_chain_eligible_count, "
                        + "reachable_count, candidate_evaluated, candidates_json, winner_vehicle_id, "
                        + "winner_vehicle_code, winner_score, runner_up_score, score_gap, tie_count, "
                        + "fail_reason, generated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setLong(1, 42L);
            ps.setString(2, "OD-2026-42");
            ps.setString(3, "RULE");
            ps.setString(4, "rule-v1");
            ps.setString(5, "FORECAST");
            ps.setObject(6, shadowAgreed);
            ps.setObject(7, shadowRegret);
            ps.setInt(8, candidatesJson.length() > 2 ? 8 : 0);
            ps.setInt(9, 7);
            ps.setInt(10, 5);
            ps.setInt(11, 4);
            ps.setInt(12, 3);
            ps.setInt(13, candidatesJson.length() > 2 ? 2 : 0);
            ps.setString(14, candidatesJson);
            ps.setObject(15, winnerId);
            ps.setString(16, winnerId == null ? null : "SIM-" + winnerId);
            ps.setObject(17, winnerScore);
            ps.setObject(18, runnerUpScore);
            ps.setObject(19, scoreGap);
            ps.setObject(20, tieCount);
            ps.setString(21, failReason);
            ps.setTimestamp(22, new java.sql.Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("审计找出被篡改的 scoreGap，影子面与漏斗按口径统计")
    void auditFlagsInconsistentGapAndKeepsContext() throws Exception {
        List<SnapshotRow> rows = DecisionReplay.readSnapshots(connection, 100);
        assertEquals(3, rows.size());
        AuditResult result = DecisionReplay.auditAll(rows, connection);
        assertEquals(0, result.candidateJsonErrors(), "合法 JSON 不许报解析错误");
        assertEquals(1, result.consistencyViolations(), "恰好 1 条（被篡改的 scoreGap）");
        assertEquals(1, result.shadowRows());
        assertEquals(1.0D, result.shadowAgreedRate(), 0D);
        assertEquals(0.5D, result.shadowRegretMean(), 0D);
        assertEquals(2L, result.orderStatusCounts().get("COMPLETED"));
        assertEquals(2L, result.vehicleStatusCounts().get("ONLINE"));
        assertEquals(5L, result.telemetryPoints());
        assertEquals(16.0D / 3.0D, result.funnelMeans().get("candidate_total"), 1e-9,
                "漏斗均值：两行 8 + 一行 0");
    }

    @Test
    @DisplayName("报告带复核清单与边界声明；main 入口对 H2 全程可跑并落盘")
    void reportListsViolationsAndWritesFile() throws Exception {
        AuditResult result = DecisionReplay.auditAll(
                DecisionReplay.readSnapshots(connection, 100), connection);
        String markdown = DecisionReplay.report(result);
        assertTrue(markdown.contains("1 行需要人工复核"));
        assertTrue(markdown.contains("scoreGapConsistent=false"), "违规行要列出具体维度");
        assertTrue(markdown.contains("不做反事实"), "分量级审计的边界必须写在报告里");

        Path out = Path.of("target", "decision-replay-test.md");
        DecisionReplay.main(new String[]{
                "--url", "jdbc:h2:mem:replay;MODE=MySQL", "--user", "sa", "--password", "",
                "--limit", "100", "--out", out.toString()});
        assertTrue(Files.exists(out));
        assertTrue(Files.readString(out).contains("决策快照回放审计"));
    }
}

package com.fsd.bootstrap.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.bootstrap.FsdCoreApplication;
import com.fsd.common.enums.AdminRole;
import com.fsd.dispatch.event.DispatchEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 路线图 §7.4「暂停开关要能审计」：谁、什么时候、因为什么按下了紧急停止。
 *
 * <p>走完整 HTTP 栈而不是单测，是因为这三列（{@code pause_reason} / {@code paused_by} /
 * {@code paused_at}）在本项目历史上**从来没有被写过一次** —— V43 建了表，写路径却硬编码
 * {@code paused_by='dispatch-api'}、原因列无人赋值。单测只会跟着实现一起错，只有真 SQL 落库
 * 再读回来才证明"列存在且被填"。</p>
 *
 * <p>身份用 {@code requestAttr} 直接注入：测试 {@code yml} 里 {@code fsd.security.admin.enabled=false}
 * 只关掉拦截器，控制器里显式的 {@code requireAdmin} 仍然生效（与 {@code AdminDispatchPauseController}
 * 的既有行为一致）。真实的登录口令不在仓库里，也不该出现在测试里。</p>
 */
@SpringBootTest(classes = FsdCoreApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.task.scheduling.enabled=false",
        "fsd.park.simulation.enabled=false",
        "fsd.fleet.telemetry.scheduler-enabled=false",
        "fsd.report.mail.enabled=false",
        "fsd.peak-mode.cron-enabled=false"
})
class AdminDispatchPauseAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    /** 测试环境没有 Redis；暂停开关的写路径只用它逐出缓存，不参与判定。 */
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    /** 测试 yml 排除了 Rabbit 自动配置 ⇒ 没有 RabbitTemplate，发布器必须顶掉（与既有集成测试同一做法）。 */
    @MockBean(name = "rabbitDispatchEventPublisher")
    private DispatchEventPublisher dispatchEventPublisher;

    @BeforeEach
    void setUp() {
        IntegrationTestSchema.recreateSchema(jdbcTemplate);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOps = (ValueOperations<String, String>) org.mockito.Mockito
                .mock(ValueOperations.class);
        when(valueOps.get(any(String.class))).thenReturn(null);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminRequest(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder) {
        return builder
                .requestAttr(AdminAuthSupport.ADMIN_ROLE_ATTRIBUTE, AdminRole.ADMIN.name())
                .requestAttr(AdminAuthSupport.ADMIN_USER_ID_ATTRIBUTE, 1L)
                .requestAttr(AdminAuthSupport.ADMIN_USERNAME_ATTRIBUTE, "admin-audit")
                .requestAttr(AdminAuthSupport.ADMIN_DISPLAY_NAME_ATTRIBUTE, "审计测试账号");
    }

    @Test
    void pausingRecordsReasonAndSessionOperatorAndReadsBack() throws Exception {
        mockMvc.perform(adminRequest(post("/api/admin/dispatch/pause"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Body(null, true, "暴雨封路"))))
                .andExpect(status().isOk());

        PauseRow row = globalRow();
        assertEquals(1, row.isPaused(), "全局档必须真的落到 park_id=0 这一行");
        assertEquals("暴雨封路", row.pauseReason());
        assertEquals("admin-audit", row.pausedBy());
        assertNull(row.resumedAt());

        mockMvc.perform(adminRequest(get("/api/admin/dispatch/pause")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"data\":{\"globalPaused\":true,"
                        + "\"pauseReason\":\"暴雨封路\",\"pausedBy\":\"admin-audit\"}}"));
    }

    /** 没有原因的紧急停止事后无法复盘 ⇒ 服务端拒，且库里不能留下半成品行。 */
    @Test
    void pausingWithoutReasonIsRejectedAndWritesNothing() throws Exception {
        mockMvc.perform(adminRequest(post("/api/admin/dispatch/pause"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Body(null, true, "  "))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "DISPATCH_PAUSE_REASON_REQUIRED")));

        assertNull(globalRowOrNull(), "被拒的写不应建行");
    }

    /**
     * 操作人只能来自会话：请求体里塞 {@code pausedBy} 不许改写审计列。
     * 这是"审计可造假"与"审计可用"的分界，所以宁可多写一条。
     */
    @Test
    void bodyCannotForgeTheOperatorColumn() throws Exception {
        mockMvc.perform(adminRequest(post("/api/admin/dispatch/pause"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parkId\":null,\"paused\":true,\"reason\":\"演练\","
                                + "\"pausedBy\":\"attacker\",\"operator\":\"attacker\"}"))
                .andExpect(status().isOk());

        assertEquals("admin-audit", globalRow().pausedBy(), "审计列必须是服务端会话身份，不是请求体字段");
    }

    private record PauseRow(Integer isPaused, String pauseReason, String pausedBy, Object resumedAt) {
    }

    private PauseRow globalRow() {
        PauseRow row = globalRowOrNull();
        if (row == null) {
            throw new AssertionError("t_dispatch_pause_state 里没有全局档（park_id=0）的行");
        }
        return row;
    }

    private PauseRow globalRowOrNull() {
        return jdbcTemplate.query(
                "SELECT is_paused, pause_reason, paused_by, resumed_at FROM t_dispatch_pause_state WHERE park_id = 0",
                rs -> rs.next() ? new PauseRow(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getObject(4)) : null);
    }

    private record Body(Long parkId, boolean paused, String reason) {
    }
}

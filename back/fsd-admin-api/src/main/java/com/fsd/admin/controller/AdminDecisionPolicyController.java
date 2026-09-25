package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.config.DispatchPolicyProperties;
import com.fsd.dispatch.policy.DecisionPolicyRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 决策策略晋级开关（路线图 §2.2「回退开关必须一分钟内可切」）。
 *
 * <p>配置文件改 {@code fsd.dispatch.policy.mode} 需要重启后端，那不是一分钟；这里给的是
 * 运行期覆盖：写进 {@link DecisionPolicyRouter} 的 volatile 字段，下一单立即生效，
 * {@code reset=true} 交还给配置文件值。
 *
 * <p>刻意不记录操作审计表：本接口只改**决策路由**这一个内存状态，且 §7.3 的策略降级指标
 * （{@code dispatchflow.dispatch.policy.fallback}）与快照里的 policyId 已经把"谁在决策"留了痕。
 */
@RestController
@RequestMapping("/api/admin/dispatch/policy")
@Tag(name = "Dispatch Policy Stage", description = "Runtime SHADOW/GRAY/PRIMARY stage and challenger routing")
@SecurityRequirement(name = "adminToken")
public class AdminDecisionPolicyController {

    private final DecisionPolicyRouter router;
    private final DispatchPolicyProperties properties;

    public AdminDecisionPolicyController(DecisionPolicyRouter router, DispatchPolicyProperties properties) {
        this.router = router;
        this.properties = properties;
    }

    @GetMapping
    @Operation(summary = "Current effective policy stage", description = "Shows config value, runtime override and routable policies")
    public ApiResponse<Map<String, Object>> status(HttpServletRequest request) {
        AdminAuthSupport.requireAuth(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stage", router.effectiveMode().name());
        body.put("challenger", router.effectiveChallengerId());
        body.put("grayPercent", router.effectiveGrayPercent());
        body.put("runtimeOverride", router.overrideActive());
        body.put("configuredStage", properties.getMode().name());
        body.put("configuredChallenger", properties.getChallenger());
        body.put("configuredGrayPercent", properties.getGrayPercent());
        body.put("batchEnabled", properties.isBatchEnabled());
        body.put("batchAlgorithm", properties.getBatchAlgorithm());
        body.put("routablePolicies", new TreeSet<>(router.registeredPolicyIds()));
        return ApiResponse.success(body);
    }

    @PostMapping
    @Operation(summary = "Switch policy stage at runtime",
            description = "mode=OFF|SHADOW|GRAY|PRIMARY; reset=true hands control back to the config file")
    public ApiResponse<Map<String, Object>> switchStage(@RequestParam(required = false) String mode,
                                                        @RequestParam(required = false) Integer grayPercent,
                                                        @RequestParam(required = false) String challenger,
                                                        @RequestParam(defaultValue = "false") boolean reset,
                                                        HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        if (reset) {
            router.clearOverride();
        } else {
            DispatchPolicyProperties.Mode parsed = mode == null || mode.isBlank()
                    ? null : DispatchPolicyProperties.Mode.valueOf(mode.trim().toUpperCase(java.util.Locale.ROOT));
            if (grayPercent != null && (grayPercent < 0 || grayPercent > 100)) {
                return ApiResponse.failure("BAD_REQUEST", "grayPercent 必须在 0-100");
            }
            // 必须与 DecisionPolicyRouter.applyOverride 用同一个 Locale.ROOT 归一化：
            // 这里若沿用默认 locale，注册的策略 id 比对就会在特定 locale 下与存进去的值差一个字符而误判"未注册"。
            if (challenger != null && !challenger.isBlank()
                    && !router.registeredPolicyIds().contains(challenger.trim().toUpperCase(java.util.Locale.ROOT))) {
                return ApiResponse.failure("BAD_REQUEST", "未注册的策略标识：" + challenger);
            }
            router.applyOverride(parsed, grayPercent, challenger);
        }
        return status(request);
    }
}

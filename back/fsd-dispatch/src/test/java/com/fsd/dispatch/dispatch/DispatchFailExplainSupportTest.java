package com.fsd.dispatch.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * §7.2 失败原因标签：新编码必须有对外的说法与可操作建议，否则前端只能显示英文原文。
 */
class DispatchFailExplainSupportTest {

    @Test
    @DisplayName("NO_MATCHING_VEHICLE 有独立标签，且原文诊断优先展示")
    void noMatchingVehicleHasItsOwnLabelAndKeepsTheDiagnosis() {
        String raw = "No idle vehicle satisfies the order constraints (SOC-passing candidates: 3; "
                + "survivors per filter {MAINTENANCE=0}; binding: MAINTENANCE)";

        DispatchFailExplainSupport.ExplainResult explained =
                DispatchFailExplainSupport.explain("NO_MATCHING_VEHICLE", raw);

        assertEquals("NO_MATCHING_VEHICLE", explained.reasonCode());
        assertEquals(raw, explained.reasonMessage(), "逐层存活数是最有用的信息，不能被通用文案盖掉");
        assertTrue(explained.suggestions().stream().anyMatch(s -> s.contains("维保")),
                () -> "建议里要能看出约束分哪几层：" + explained.suggestions());
    }

    @Test
    @DisplayName("没有原文时给出中文兜底，且不会退化成 LOW_BATTERY")
    void blankRawMessageFallsBackToChineseNotLowBattery() {
        DispatchFailExplainSupport.ExplainResult explained =
                DispatchFailExplainSupport.explain("NO_MATCHING_VEHICLE", "  ");

        assertEquals("NO_MATCHING_VEHICLE", explained.reasonCode());
        assertTrue(explained.reasonMessage().contains("约束"), explained.reasonMessage());
        assertTrue(explained.suggestions().size() >= 2);
    }

    @Test
    @DisplayName("建议链接指向车辆页，和 NO_VEHICLE/LOW_BATTERY 同一去处")
    void suggestionLinksPointAtVehicles() {
        List<String> links = DispatchFailExplainSupport.suggestionLinks("NO_MATCHING_VEHICLE");

        assertEquals(List.of("vehicles"), links);
    }
}

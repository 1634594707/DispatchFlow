package com.fsd.dispatch.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Maps internal assign failure codes to user-facing reason + actionable suggestions (Phase 11.3).
 */
public final class DispatchFailExplainSupport {

    private DispatchFailExplainSupport() {
    }

    public record ExplainResult(String reasonCode, String reasonMessage, List<String> suggestions) {
    }

    public static ExplainResult explain(String internalCode, String rawMessage) {
        String code = normalizeCode(internalCode);
        return switch (code) {
            case "NO_IDLE_VEHICLE" -> new ExplainResult(
                    "NO_IDLE_VEHICLE",
                    "当前无在线空闲车辆可派",
                    List.of("打开车辆列表，确认在线且空闲车辆数量", "检查是否有车辆卡在充电/异常状态", "可对任务执行手动派车或批量改派"));
            case "LOW_BATTERY" -> new ExplainResult(
                    "LOW_BATTERY",
                    "空闲车辆电量低于可派车阈值",
                    List.of("引导低电量车辆前往充电桩", "查看充电报表与桩位占用", "待 SOC 恢复后重新自动派车"));
            case "ROUTE_BLOCKED" -> new ExplainResult(
                    "ROUTE_BLOCKED",
                    rawMessage != null && !rawMessage.isBlank() ? rawMessage : "取货点路网不可达或途经路段被管制",
                    List.of("打开路网管理，检查禁用路段与节点", "查看交通态势，处理高拥堵路段", "确认取货站点坐标在路网范围内"));
            case "HUB_CAPACITY_FULL" -> new ExplainResult(
                    "HUB_CAPACITY_FULL",
                    rawMessage != null && !rawMessage.isBlank() ? rawMessage : "枢纽/母港容量已满，暂无法派车",
                    List.of("打开母港分流视图查看占用", "改派至其他枢纽或缓冲站点", "联系现场释放枢纽容量"));
            case "ROUTE_OCCUPANCY_FULL" -> new ExplainResult(
                    "ROUTE_OCCUPANCY_FULL",
                    rawMessage != null && !rawMessage.isBlank() ? rawMessage : "线路并发任务已达上限",
                    List.of("查看线路占用与运营时段", "切换高峰预案提升吞吐", "等待在途任务完成后再派"));
            case "CONFLICT" -> new ExplainResult(
                    "CONFLICT",
                    rawMessage != null && !rawMessage.isBlank() ? rawMessage : "车辆占车或占桩冲突",
                    List.of("确认目标车辆未被其他任务占用", "检查充电桩/停车位释放状态", "尝试改派其他车辆"));
            default -> new ExplainResult(
                    code,
                    rawMessage != null && !rawMessage.isBlank() ? rawMessage : "自动派车失败，需人工介入",
                    List.of("查看异常队列与任务详情", "尝试手动派车或批量改派"));
        };
    }

    public static String normalizeCode(String internalCode) {
        if (internalCode == null || internalCode.isBlank()) {
            return "NO_IDLE_VEHICLE";
        }
        return switch (internalCode.trim().toUpperCase(Locale.ROOT)) {
            case "NO_VEHICLE" -> "NO_IDLE_VEHICLE";
            case "LOW_SOC" -> "LOW_BATTERY";
            case "UNREACHABLE", "ZONE_PAUSED" -> "ROUTE_BLOCKED";
            default -> internalCode.trim().toUpperCase(Locale.ROOT);
        };
    }

    public static List<String> suggestionLinks(String reasonCode) {
        List<String> links = new ArrayList<>();
        switch (normalizeCode(reasonCode)) {
            case "NO_IDLE_VEHICLE", "LOW_BATTERY" -> links.add("vehicles");
            case "ROUTE_BLOCKED" -> {
                links.add("road-network");
                links.add("traffic");
            }
            case "HUB_CAPACITY_FULL", "ROUTE_OCCUPANCY_FULL" -> links.add("hub-overview");
            default -> links.add("exceptions");
        }
        return links;
    }
}

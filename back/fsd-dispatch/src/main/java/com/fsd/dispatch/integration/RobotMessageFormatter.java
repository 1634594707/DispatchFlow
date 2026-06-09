package com.fsd.dispatch.integration;

import com.fsd.dispatch.event.DispatchDomainEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Formats dispatch domain events into platform-specific robot webhook message payloads.
 */
@Component
public class RobotMessageFormatter {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * Build the JSON payload for a given channel type.
     *
     * @param channelType GENERIC | WECHAT_BOT | DINGTALK_BOT | FEISHU_BOT
     * @param event       the domain event
     * @return JSON string payload
     */
    public String format(String channelType, DispatchDomainEvent event) {
        String title = buildTitle(event);
        String content = buildContent(event);
        return switch (channelType) {
            case "WECHAT_BOT" -> wechatBotPayload(content);
            case "DINGTALK_BOT" -> dingtalkBotPayload(title, content);
            case "FEISHU_BOT" -> feishuBotPayload(title, content);
            default -> genericPayload(event);
        };
    }

    private String buildTitle(DispatchDomainEvent event) {
        String typeLabel = eventTypeLabel(event.getEventType());
        return "🚨 " + typeLabel + " · " + event.getBusinessKey();
    }

    private String buildContent(DispatchDomainEvent event) {
        String typeLabel = eventTypeLabel(event.getEventType());
        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(typeLabel).append("\n\n");
        sb.append("- **事件类型**：").append(event.getEventType()).append("\n");
        sb.append("- **业务编号**：").append(event.getBusinessKey() != null ? event.getBusinessKey() : "-").append("\n");
        sb.append("- **发生时间**：").append(event.getEventTime() != null ? event.getEventTime() : LocalDateTime.now().format(DTF)).append("\n");
        if (event.getPayload() != null ) {
            String payloadStr = event.getPayload().toString();
            if (!payloadStr.isBlank()) {
                sb.append("- **详情**：").append(payloadStr).append("\n");
            }
        }
        return sb.toString();
    }

    private String wechatBotPayload(String content) {
        // Escape markdown reserved chars for WeChat
        String safe = content
                .replace("```", "「代码块」")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "{\"msgtype\":\"markdown\",\"markdown\":{\"content\":\""
                + escapeJson(safe) + "\"}}";
    }

    private String dingtalkBotPayload(String title, String content) {
        String safe = content.replace("```", "「代码块」");
        return "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\""
                + escapeJson(title) + "\",\"text\":\""
                + escapeJson(safe) + "\"}}";
    }

    private String feishuBotPayload(String title, String content) {
        String mdContent = content.replace("\"", "\\\"");
        return String.format(Locale.ROOT,
                "{\"msg_type\":\"interactive\",\"card\":{\"header\":{\"title\":{\"tag\":\"plain_text\",\"content\":\"%s\"}},\"elements\":[{\"tag\":\"markdown\",\"content\":\"%s\"}]}}",
                escapeJson(title),
                escapeJson(mdContent));
    }

    private String genericPayload(DispatchDomainEvent event) {
        return "{\"eventType\":\"" + escapeJson(event.getEventType())
                + "\",\"businessKey\":\"" + escapeJson(event.getBusinessKey())
                + "\",\"eventTime\":\"" + (event.getEventTime() != null ? event.getEventTime() : LocalDateTime.now().toString())
                + "\"}";
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String eventTypeLabel(String eventType) {
        if (eventType == null) return "调度事件";
        return switch (eventType) {
            case "dispatch.task.created" -> "任务创建";
            case "dispatch.task.assigned" -> "任务已派车";
            case "dispatch.task.executing" -> "任务执行中";
            case "dispatch.task.success" -> "任务成功";
            case "dispatch.task.failed" -> "任务失败";
            case "dispatch.task.cancelled" -> "任务取消";
            case "dispatch.exception.open" -> "异常打开";
            case "dispatch.exception.resolved" -> "异常已解决";
            case "dispatch.hub.arrival" -> "枢纽到达";
            case "dispatch.hub.departure" -> "枢纽离开";
            default -> eventType;
        };
    }
}
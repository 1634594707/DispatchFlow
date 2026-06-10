package com.fsd.admin.service.impl;

import com.fsd.admin.dto.AdminAssistantRequest;
import com.fsd.admin.service.DispatchAssistantAdminService;
import com.fsd.admin.vo.AdminAssistantAction;
import com.fsd.admin.vo.AdminAssistantResponse;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.dispatch.service.ParkPilotService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DispatchAssistantAdminServiceImpl implements DispatchAssistantAdminService {

    private static final Pattern TASK_NO_PATTERN = Pattern.compile("(TSK-[A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VEHICLE_CODE_PATTERN = Pattern.compile("(PARK-[A-Z0-9-]+|REAL-[A-Z0-9-]+|VEH-[A-Z0-9-]+)", Pattern.CASE_INSENSITIVE);

    private final DispatchAdminQueryService dispatchAdminQueryService;
    private final DispatchExceptionService dispatchExceptionService;
    private final ParkPilotService parkPilotService;

    public DispatchAssistantAdminServiceImpl(DispatchAdminQueryService dispatchAdminQueryService,
                                             DispatchExceptionService dispatchExceptionService,
                                             ParkPilotService parkPilotService) {
        this.dispatchAdminQueryService = dispatchAdminQueryService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.parkPilotService = parkPilotService;
    }

    @Override
    public AdminAssistantResponse interpret(AdminAssistantRequest request) {
        String text = request.getInstruction() == null ? "" : request.getInstruction().trim();
        String lower = text.toLowerCase(Locale.ROOT);
        DispatchInterventionQueueResponse queue = dispatchAdminQueryService.getInterventionQueue();
        List<ParkVehicleSnapshotResponse> vehicles = parkPilotService.listVehicleSnapshots();
        if (!StringUtils.hasText(text)) {
            return buildGeneral(queue, vehicles, "请输入调度指令，例如：刷新工作台、批量自动派车、查看低电量车辆。");
        }
        if (containsAny(lower, "刷新", "reload", "refresh")) {
            return AdminAssistantResponse.builder()
                    .intent("REFRESH_WORKBENCH")
                    .reply("将刷新调度工作台与园区态势。")
                    .suggestions(List.of("在工作台按 R 可快速刷新"))
                    .actions(List.of(action("REFRESH_WORKBENCH", "刷新工作台", Map.of())))
                    .build();
        }
        if (containsAny(lower, "自动派车", "批量派车", "一键派车")) {
            int pending = safeSize(queue.getManualPendingTasks()) + countPendingTasks();
            return AdminAssistantResponse.builder()
                    .intent("BATCH_AUTO_ASSIGN")
                    .reply("当前约有 " + pending + " 个任务可尝试自动派车，确认后将对待派/人工待处理任务执行批量自动派车。")
                    .suggestions(List.of("建议先查看可派车辆数量是否充足"))
                    .actions(List.of(action("BATCH_AUTO_ASSIGN", "批量自动派车", Map.of("scope", "INTERVENTION"))))
                    .build();
        }
        if (containsAny(lower, "低电", "充电", "电量")) {
            long lowBattery = vehicles.stream().filter(v -> Boolean.TRUE.equals(v.getLowBattery())).count();
            long charging = vehicles.stream().filter(v -> Boolean.TRUE.equals(v.getCharging())).count();
            List<String> suggestions = new ArrayList<>();
            suggestions.add("低电量车辆 " + lowBattery + " 台，充电中 " + charging + " 台");
            if (lowBattery > 0) {
                suggestions.add("可为低电量车辆创建充电任务或引导至充电桩");
            }
            return AdminAssistantResponse.builder()
                    .intent("CHARGING_ADVICE")
                    .reply("已分析园区车辆电量态势。")
                    .suggestions(suggestions)
                    .actions(List.of(
                            action("NAVIGATE", "打开车辆管理", Map.of("path", "/vehicles")),
                            action("NAVIGATE", "打开充电报表", Map.of("path", "/analytics/charging"))))
                    .build();
        }
        if (containsAny(lower, "异常", "告警", "故障")) {
            int open = safeSize(queue.getOpenExceptions());
            List<String> suggestions = new ArrayList<>();
            suggestions.add("当前 OPEN 异常 " + open + " 条");
            if (open > 0) {
                suggestions.add("优先处理 MANUAL_PENDING 且带异常的任务");
                suggestions.add("可对超时任务执行改派或标记失败");
            }
            return AdminAssistantResponse.builder()
                    .intent("EXCEPTION_ADVICE")
                    .reply(open > 0 ? "建议先进入异常队列逐条处置。" : "当前无 OPEN 异常，可继续关注待派任务。")
                    .suggestions(suggestions)
                    .actions(List.of(action("NAVIGATE", "打开异常任务", Map.of("path", "/exceptions"))))
                    .build();
        }
        if (containsAny(lower, "手动派车", "改派", "指派")) {
            return AdminAssistantResponse.builder()
                    .intent("MANUAL_ASSIGN_HINT")
                    .reply("请在工作台选中任务后按 M 打开手动派车，或拖拽调整任务优先级。")
                    .suggestions(List.of("按 ↑↓ 可切换任务焦点"))
                    .actions(List.of(action("NAVIGATE", "前往工作台", Map.of("path", "/workbench"))))
                    .build();
        }
        Matcher taskMatcher = TASK_NO_PATTERN.matcher(text);
        if (taskMatcher.find()) {
            String taskNo = taskMatcher.group(1).toUpperCase(Locale.ROOT);
            DispatchTaskListItemResponse task = dispatchAdminQueryService.listTasks().stream()
                    .filter(item -> taskNo.equalsIgnoreCase(item.getTaskNo()))
                    .findFirst()
                    .orElse(null);
            if (task != null) {
                return AdminAssistantResponse.builder()
                        .intent("OPEN_TASK")
                        .reply("已定位任务 " + taskNo + "，状态 " + task.getStatus())
                        .suggestions(List.of("可执行自动派车或查看详情"))
                        .actions(List.of(
                                action("NAVIGATE", "打开任务详情", Map.of("path", "/tasks/" + task.getTaskId())),
                                action("AUTO_ASSIGN_TASK", "自动派车", Map.of("taskId", task.getTaskId()))))
                        .build();
            }
        }
        Matcher vehicleMatcher = VEHICLE_CODE_PATTERN.matcher(text);
        if (vehicleMatcher.find()) {
            String vehicleCode = vehicleMatcher.group(1).toUpperCase(Locale.ROOT);
            ParkVehicleSnapshotResponse vehicle = vehicles.stream()
                    .filter(item -> vehicleCode.equalsIgnoreCase(item.getVehicleCode()))
                    .findFirst()
                    .orElse(null);
            if (vehicle != null) {
                return AdminAssistantResponse.builder()
                        .intent("OPEN_VEHICLE")
                        .reply("已定位车辆 " + vehicleCode + "，电量 " + vehicle.getBatteryLevel() + "%")
                        .suggestions(List.of("dispatchStatus=" + vehicle.getDispatchStatus()))
                        .actions(List.of(action("NAVIGATE", "打开车辆详情", Map.of("path", "/vehicles/" + vehicle.getVehicleId()))))
                        .build();
            }
        }
        return buildGeneral(queue, vehicles,
                "暂未识别该指令。可尝试：刷新工作台、批量自动派车、查看低电量车辆、处理异常。");
    }

    private AdminAssistantResponse buildGeneral(DispatchInterventionQueueResponse queue,
                                                List<ParkVehicleSnapshotResponse> vehicles,
                                                String reply) {
        int pending = countPendingTasks();
        int manual = safeSize(queue.getManualPendingTasks());
        int openExc = safeSize(queue.getOpenExceptions());
        long lowBattery = vehicles.stream().filter(v -> Boolean.TRUE.equals(v.getLowBattery())).count();
        List<String> suggestions = List.of(
                "待派任务 " + pending + " · 人工待处理 " + manual + " · OPEN 异常 " + openExc,
                "低电量车辆 " + lowBattery + " 台",
                "支持语音输入（浏览器 Web Speech API）");
        return AdminAssistantResponse.builder()
                .intent("GENERAL")
                .reply(reply)
                .suggestions(suggestions)
                .actions(List.of(
                        action("NAVIGATE", "调度工作台", Map.of("path", "/workbench")),
                        action("NAVIGATE", "数字孪生", Map.of("path", "/digital-twin")),
                        action("NAVIGATE", "系统健康", Map.of("path", "/system/health"))))
                .build();
    }

    private int countPendingTasks() {
        return (int) dispatchAdminQueryService.listTasks().stream()
                .filter(task -> "PENDING".equals(task.getStatus()) || "MANUAL_PENDING".equals(task.getStatus()))
                .count();
    }

    private int safeSize(List<?> list) {
        return list == null ? 0 : list.size();
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private AdminAssistantAction action(String type, String label, Map<String, Object> payload) {
        return AdminAssistantAction.builder()
                .actionType(type)
                .label(label)
                .payload(payload)
                .build();
    }
}

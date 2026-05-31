package com.fsd.dispatch.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fsd.common.enums.DispatchTaskStatus;
import com.fsd.common.enums.VehicleCommandStatus;
import com.fsd.common.enums.VehicleCommandType;
import com.fsd.common.enums.VehicleDispatchStatus;
import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.VehicleCommandEntity;
import com.fsd.dispatch.fleet.vda5050.Vda5050DispatchPublisher;
import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.mapper.VehicleCommandMapper;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskOperateLogService;
import com.fsd.dispatch.service.DispatchTaskStateService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.service.VehicleCommandService;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.VehicleCommandResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.service.OrderStateService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleCommandServiceImpl implements VehicleCommandService {

    private final VehicleCommandMapper vehicleCommandMapper;
    private final DispatchTaskMapper dispatchTaskMapper;
    private final DispatchTaskStateService dispatchTaskStateService;
    private final DispatchTaskOperateLogService operateLogService;
    private final DispatchExceptionService dispatchExceptionService;
    private final VehicleService vehicleService;
    private final ParkStationService parkStationService;
    private final OrderStateService orderStateService;
    private final ObjectMapper objectMapper;
    private final Vda5050DispatchPublisher vda5050OrderPublisher;

    public VehicleCommandServiceImpl(VehicleCommandMapper vehicleCommandMapper,
                                     DispatchTaskMapper dispatchTaskMapper,
                                     DispatchTaskStateService dispatchTaskStateService,
                                     DispatchTaskOperateLogService operateLogService,
                                     DispatchExceptionService dispatchExceptionService,
                                     VehicleService vehicleService,
                                     ParkStationService parkStationService,
                                     OrderStateService orderStateService,
                                     ObjectMapper objectMapper,
                                     Vda5050DispatchPublisher vda5050OrderPublisher) {
        this.vehicleCommandMapper = vehicleCommandMapper;
        this.dispatchTaskMapper = dispatchTaskMapper;
        this.dispatchTaskStateService = dispatchTaskStateService;
        this.operateLogService = operateLogService;
        this.dispatchExceptionService = dispatchExceptionService;
        this.vehicleService = vehicleService;
        this.parkStationService = parkStationService;
        this.orderStateService = orderStateService;
        this.objectMapper = objectMapper;
        this.vda5050OrderPublisher = vda5050OrderPublisher;
    }

    @Override
    @Transactional
    public void issueDispatchCommandIfNeeded(VehicleEntity vehicle, DispatchTaskEntity task, OrderEntity order) {
        if (!VehicleLinkMode.issuesExternalCommands(resolveLinkMode(vehicle))) {
            return;
        }
        ParkStationResponse pickup = parkStationService.requireStation(order.getPickupPointId());
        ParkStationResponse dropoff = parkStationService.requireStation(order.getDropoffPointId());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("taskId", task.getId());
        payload.put("orderId", order.getId());
        payload.put("pickupStationId", order.getPickupPointId());
        payload.put("dropoffStationId", order.getDropoffPointId());
        payload.put("pickupStationCode", pickup.getStationCode());
        payload.put("dropoffStationCode", dropoff.getStationCode());

        VehicleCommandEntity command = new VehicleCommandEntity();
        command.setVehicleId(vehicle.getId());
        command.setTaskId(task.getId());
        command.setOrderId(order.getId());
        command.setCommandType(VehicleCommandType.DISPATCH_TASK.name());
        command.setCommandStatus(VehicleCommandStatus.PENDING.name());
        command.setPayloadJson(writePayload(payload));
        command.setIssuedAt(LocalDateTime.now());
        vehicleCommandMapper.insert(command);

        if (VehicleLinkMode.VDA5050.name().equals(resolveLinkMode(vehicle))) {
            vda5050OrderPublisher.publishDispatchOrder(vehicle, command, task, pickup, dropoff);
        }

        operateLogService.record(task.getId(), "ISSUE_COMMAND", task.getStatus(), task.getStatus(),
                "SYSTEM", "gateway", "gateway", "Dispatch command issued to real vehicle");
    }

    @Override
    @Transactional
    public VehicleCommandResponse pollNextCommand(String vehicleCode) {
        VehicleEntity vehicle = vehicleService.getByVehicleCode(vehicleCode);
        VehicleCommandEntity command = vehicleCommandMapper.selectOne(new LambdaQueryWrapper<VehicleCommandEntity>()
                .eq(VehicleCommandEntity::getVehicleId, vehicle.getId())
                .eq(VehicleCommandEntity::getCommandStatus, VehicleCommandStatus.PENDING.name())
                .orderByAsc(VehicleCommandEntity::getIssuedAt)
                .last("limit 1"));
        if (command == null) {
            return null;
        }
        command.setCommandStatus(VehicleCommandStatus.DELIVERED.name());
        command.setDeliveredAt(LocalDateTime.now());
        vehicleCommandMapper.updateById(command);
        return toResponse(command);
    }

    @Override
    @Transactional
    public void acknowledgeCommand(String vehicleCode, Long commandId) {
        VehicleCommandEntity command = requireOwnedCommand(vehicleCode, commandId);
        if (VehicleCommandStatus.ACKED.name().equals(command.getCommandStatus())) {
            return;
        }
        if (!VehicleCommandStatus.DELIVERED.name().equals(command.getCommandStatus())
                && !VehicleCommandStatus.PENDING.name().equals(command.getCommandStatus())) {
            throw new BusinessException("VEHICLE_COMMAND_INVALID_STATE", "Command cannot be acknowledged");
        }
        command.setCommandStatus(VehicleCommandStatus.ACKED.name());
        command.setAckedAt(LocalDateTime.now());
        vehicleCommandMapper.updateById(command);
    }

    @Override
    @Transactional
    public void failCommand(String vehicleCode, Long commandId, String reason) {
        VehicleEntity vehicle = vehicleService.getByVehicleCode(vehicleCode);
        VehicleCommandEntity command = requireOwnedCommand(vehicleCode, commandId);
        if (VehicleCommandStatus.FAILED.name().equals(command.getCommandStatus())) {
            return;
        }
        command.setCommandStatus(VehicleCommandStatus.FAILED.name());
        command.setFailReason(reason);
        command.setFailedAt(LocalDateTime.now());
        vehicleCommandMapper.updateById(command);

        DispatchTaskEntity task = dispatchTaskStateService.getTask(command.getTaskId());
        String beforeStatus = task.getStatus();
        task.setStatus(DispatchTaskStatus.MANUAL_PENDING.name());
        task.setFailReasonCode("COMMAND_FAILED");
        task.setFailReasonMsg(reason);
        task.setVehicleId(null);
        dispatchTaskMapper.updateById(task);
        orderStateService.revertToWaitingDispatch(task.getOrderId());
        vehicleService.releaseVehicle(vehicle.getId(), VehicleDispatchStatus.IDLE.name());
        dispatchExceptionService.recordException(task.getId(), task.getOrderId(), vehicle.getId(),
                "COMMAND_FAILED", reason);
        operateLogService.record(task.getId(), "COMMAND_FAILED", beforeStatus, task.getStatus(),
                "VEHICLE", vehicleCode, vehicleCode, reason);
    }

    private VehicleCommandEntity requireOwnedCommand(String vehicleCode, Long commandId) {
        VehicleEntity vehicle = vehicleService.getByVehicleCode(vehicleCode);
        VehicleCommandEntity command = vehicleCommandMapper.selectById(commandId);
        if (command == null || !vehicle.getId().equals(command.getVehicleId())) {
            throw new BusinessException("VEHICLE_COMMAND_NOT_FOUND", "Vehicle command not found");
        }
        return command;
    }

    private VehicleCommandResponse toResponse(VehicleCommandEntity command) {
        Map<String, Object> payload = readPayload(command.getPayloadJson());
        return VehicleCommandResponse.builder()
                .commandId(command.getId())
                .commandType(command.getCommandType())
                .commandStatus(command.getCommandStatus())
                .taskId(command.getTaskId())
                .orderId(command.getOrderId())
                .pickupStationId(asLong(payload.get("pickupStationId")))
                .dropoffStationId(asLong(payload.get("dropoffStationId")))
                .pickupStationCode(asString(payload.get("pickupStationCode")))
                .dropoffStationCode(asString(payload.get("dropoffStationCode")))
                .issuedAt(command.getIssuedAt())
                .build();
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("VEHICLE_COMMAND_PAYLOAD_ERROR", "Failed to serialize command payload");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, Map.class);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("VEHICLE_COMMAND_PAYLOAD_ERROR", "Failed to parse command payload");
        }
    }

    private static String resolveLinkMode(VehicleEntity vehicle) {
        return vehicle.getLinkMode() == null || vehicle.getLinkMode().isBlank()
                ? VehicleLinkMode.SIM.name()
                : vehicle.getLinkMode();
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}

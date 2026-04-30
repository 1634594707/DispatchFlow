package com.fsd.dispatch.service.impl;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.service.ParkPilotCommandService;
import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.DispatchTaskAssignResponse;
import com.fsd.dispatch.vo.DispatchTaskCreateResponse;
import com.fsd.dispatch.vo.ParkOrderCreateResponse;
import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.service.OrderService;
import com.fsd.order.vo.OrderCreateResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParkPilotCommandServiceImpl implements ParkPilotCommandService {

    private static final DateTimeFormatter EXTERNAL_ORDER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderService orderService;
    private final DispatchTaskService dispatchTaskService;
    private final ParkPilotService parkPilotService;

    public ParkPilotCommandServiceImpl(OrderService orderService,
                                       DispatchTaskService dispatchTaskService,
                                       ParkPilotService parkPilotService) {
        this.orderService = orderService;
        this.dispatchTaskService = dispatchTaskService;
        this.parkPilotService = parkPilotService;
    }

    @Override
    @Transactional
    public ParkOrderCreateResponse createParkOrder(ParkOrderCreateRequest request) {
        parkPilotService.getStation(request.getPickupStationId());
        parkPilotService.getStation(request.getDropoffStationId());
        if (Objects.equals(request.getPickupStationId(), request.getDropoffStationId())) {
            throw new BusinessException("PARK_ORDER_STATION_INVALID", "Pickup and dropoff station cannot be the same");
        }

        OrderCreateRequest orderRequest = new OrderCreateRequest();
        orderRequest.setExternalOrderNo(resolveExternalOrderNo(request.getExternalOrderNo()));
        orderRequest.setSourceType("PARK");
        orderRequest.setBizType("DELIVERY");
        orderRequest.setPickupPointId(request.getPickupStationId());
        orderRequest.setDropoffPointId(request.getDropoffStationId());
        orderRequest.setPriority(request.getPriority() == null || request.getPriority().isBlank() ? "P2" : request.getPriority());
        orderRequest.setRemark(request.getRemark());

        OrderCreateResponse orderResponse = orderService.createOrder(orderRequest);

        DispatchTaskCreateRequest taskRequest = new DispatchTaskCreateRequest();
        taskRequest.setOrderId(orderResponse.getOrderId());
        taskRequest.setDispatchType("AUTO");
        taskRequest.setRemark(request.getRemark());
        DispatchTaskCreateResponse taskResponse = dispatchTaskService.createTask(taskRequest);
        DispatchTaskAssignResponse assignResponse = dispatchTaskService.autoAssignTask(taskResponse.getTaskId());

        return ParkOrderCreateResponse.builder()
                .orderId(orderResponse.getOrderId())
                .orderNo(orderResponse.getOrderNo())
                .orderStatus(orderResponse.getStatus())
                .taskId(taskResponse.getTaskId())
                .taskNo(taskResponse.getTaskNo())
                .taskStatus(assignResponse.getStatus())
                .vehicleId(assignResponse.getVehicleId())
                .message(assignResponse.getMessage())
                .build();
    }

    private String resolveExternalOrderNo(String externalOrderNo) {
        if (externalOrderNo != null && !externalOrderNo.isBlank()) {
            return externalOrderNo;
        }
        return "PARK-" + LocalDateTime.now().format(EXTERNAL_ORDER_FORMATTER)
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}

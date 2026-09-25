package com.fsd.dispatch.service.impl;

import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.dto.DispatchTaskCreateRequest;
import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.geo.OrderEndpointResolver;
import com.fsd.dispatch.service.DispatchRouteService;
import com.fsd.dispatch.service.ParkPilotCommandService;
import com.fsd.dispatch.service.DispatchPauseControlService;
import com.fsd.dispatch.service.ParkOrderIdempotencyService;
import com.fsd.dispatch.service.ParkStationService;
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
    private final ParkStationService parkStationService;
    private final DispatchPauseControlService dispatchPauseControlService;
    private final DispatchRouteService dispatchRouteService;
    private final ParkOrderIdempotencyService parkOrderIdempotencyService;
    private final com.fsd.dispatch.geo.OrderEndpointResolver orderEndpointResolver;

    public ParkPilotCommandServiceImpl(OrderService orderService,
                                       DispatchTaskService dispatchTaskService,
                                       ParkStationService parkStationService,
                                       DispatchPauseControlService dispatchPauseControlService,
                                       DispatchRouteService dispatchRouteService,
                                       ParkOrderIdempotencyService parkOrderIdempotencyService,
                                       com.fsd.dispatch.geo.OrderEndpointResolver orderEndpointResolver) {
        this.orderService = orderService;
        this.dispatchTaskService = dispatchTaskService;
        this.parkStationService = parkStationService;
        this.dispatchPauseControlService = dispatchPauseControlService;
        this.dispatchRouteService = dispatchRouteService;
        this.parkOrderIdempotencyService = parkOrderIdempotencyService;
        this.orderEndpointResolver = orderEndpointResolver;
    }

    private static boolean hasGeo(java.math.BigDecimal lng, java.math.BigDecimal lat) {
        return lng != null && lat != null;
    }

    @Override
    @Transactional
    public ParkOrderCreateResponse createParkOrder(ParkOrderCreateRequest request) {
        ParkEntity park = request.getParkId() == null
                ? parkStationService.requireDefaultPark()
                : parkStationService.requirePark(request.getParkId());

        // 幂等占位：重复提交直接重放原订单结果，不再创建新资源（路线图 3.3）。
        ParkOrderCreateResponse replayed = parkOrderIdempotencyService.tryReserve(request, park.getId());
        if (replayed != null) {
            return replayed;
        }

        if (dispatchPauseControlService.isDispatchPaused(park.getId())) {
            throw new BusinessException("DISPATCH_PAUSED", "当前园区已暂停新派单，暂不接受移动下单");
        }
        if (request.getPickupStationId() == null && !hasGeo(request.getPickupLng(), request.getPickupLat())) {
            throw new BusinessException("ORDER_ENDPOINT_MISSING", "取货端要给 pickupStationId 或 GCJ-02 坐标");
        }
        if (request.getDropoffStationId() == null && !hasGeo(request.getDropoffLng(), request.getDropoffLat())) {
            throw new BusinessException("ORDER_ENDPOINT_MISSING", "送货端要给 dropoffStationId 或 GCJ-02 坐标");
        }
        if (request.getPickupStationId() != null) {
            parkStationService.assertStationInPark(request.getPickupStationId(), park.getId());
            parkStationService.assertStationWithinServiceArea(request.getPickupStationId(), park.getId());
        }
        if (request.getDropoffStationId() != null) {
            parkStationService.assertStationInPark(request.getDropoffStationId(), park.getId());
            parkStationService.assertStationWithinServiceArea(request.getDropoffStationId(), park.getId());
        }
        if (request.getPickupStationId() != null && request.getDropoffStationId() != null) {
            parkStationService.assertStationsBelongToSamePark(request.getPickupStationId(), request.getDropoffStationId());
            if (Objects.equals(request.getPickupStationId(), request.getDropoffStationId())) {
                throw new BusinessException("PARK_ORDER_STATION_INVALID", "Pickup and dropoff station cannot be the same");
            }
        }

        // 受理判据（范围内 / 吸附得到 / 两端连得通）全部在这里跑完才落库，
        // 否则会出现"接单成功但永远派不出去"的死单。坐标端会被登记成一条可派单点。
        OrderEndpointResolver.Accepted accepted = orderEndpointResolver.resolveForAcceptance(
                park.getId(),
                request.getPickupStationId(), request.getPickupLng(), request.getPickupLat(),
                request.getDropoffStationId(), request.getDropoffLng(), request.getDropoffLat());
        Long pickupStationId = accepted == null ? request.getPickupStationId() : accepted.pickupStationId();
        Long dropoffStationId = accepted == null ? request.getDropoffStationId() : accepted.dropoffStationId();

        OrderCreateRequest orderRequest = new OrderCreateRequest();
        orderRequest.setExternalOrderNo(resolveExternalOrderNo(request.getExternalOrderNo()));
        orderRequest.setSourceType("PARK");
        orderRequest.setBizType("DELIVERY");
        orderRequest.setParkId(park.getId());
        Long routeId = request.getRouteId();
        if (routeId == null && pickupStationId != null && dropoffStationId != null) {
            routeId = dispatchRouteService.matchRouteByStations(park.getId(), pickupStationId, dropoffStationId)
                    .map(route -> route.getId())
                    .orElse(null);
        }
        orderRequest.setRouteId(routeId);
        orderRequest.setPickupPointId(pickupStationId);
        orderRequest.setDropoffPointId(dropoffStationId);
        orderRequest.setPickupLng(request.getPickupLng());
        orderRequest.setPickupLat(request.getPickupLat());
        orderRequest.setDropoffLng(request.getDropoffLng());
        orderRequest.setDropoffLat(request.getDropoffLat());
        orderRequest.setPriority(request.getPriority() == null || request.getPriority().isBlank() ? "P2" : request.getPriority());
        orderRequest.setRemark(request.getRemark());

        OrderCreateResponse orderResponse = orderService.createOrder(orderRequest,
                accepted == null ? null : accepted.auditResolution());

        DispatchTaskCreateRequest taskRequest = new DispatchTaskCreateRequest();
        taskRequest.setOrderId(orderResponse.getOrderId());
        taskRequest.setDispatchType("AUTO");
        taskRequest.setRemark(request.getRemark());
        DispatchTaskCreateResponse taskResponse = dispatchTaskService.createTask(taskRequest);
        DispatchTaskAssignResponse assignResponse = dispatchTaskService.autoAssignTask(taskResponse.getTaskId());

        ParkOrderCreateResponse response = ParkOrderCreateResponse.builder()
                .orderId(orderResponse.getOrderId())
                .orderNo(orderResponse.getOrderNo())
                .orderStatus(orderResponse.getStatus())
                .taskId(taskResponse.getTaskId())
                .taskNo(taskResponse.getTaskNo())
                .taskStatus(assignResponse.getStatus())
                .vehicleId(assignResponse.getVehicleId())
                .message(assignResponse.getMessage())
                .replayed(false)
                .build();
        parkOrderIdempotencyService.completeReservation(request, response);
        return response;
    }

    private String resolveExternalOrderNo(String externalOrderNo) {
        if (externalOrderNo != null && !externalOrderNo.isBlank()) {
            return externalOrderNo;
        }
        return "PARK-" + LocalDateTime.now().format(EXTERNAL_ORDER_FORMATTER)
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}

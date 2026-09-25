package com.fsd.order.service.impl;
import com.fsd.common.enums.OrderStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.dto.OrderEndpointResolution;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.order.service.OrderService;
import com.fsd.order.vo.OrderCreateResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderServiceImpl implements OrderService {

    private static final DateTimeFormatter ORDER_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request, OrderEndpointResolution resolution) {
        boolean pickupByCoord = request.getPickupLng() != null && request.getPickupLat() != null;
        boolean dropoffByCoord = request.getDropoffLng() != null && request.getDropoffLat() != null;
        if (request.getPickupPointId() == null && !pickupByCoord) {
            throw new BusinessException("ORDER_ENDPOINT_MISSING", "取货端必须给站点 ID 或 GCJ-02 坐标");
        }
        if (request.getDropoffPointId() == null && !dropoffByCoord) {
            throw new BusinessException("ORDER_ENDPOINT_MISSING", "送货端必须给站点 ID 或 GCJ-02 坐标");
        }
        // 坐标只有吸附成路网节点才可派单。没有吸附结果就落库，会得到一张"接了单但永远派不出去"的死单。
        if (pickupByCoord && (resolution == null || resolution.pickupNodeCode() == null)) {
            throw new BusinessException("ORDER_ENDPOINT_UNRESOLVED", "取货坐标未经吸附校验，不能受理");
        }
        if (dropoffByCoord && (resolution == null || resolution.dropoffNodeCode() == null)) {
            throw new BusinessException("ORDER_ENDPOINT_UNRESOLVED", "送货坐标未经吸附校验，不能受理");
        }

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(generateOrderNo());
        orderEntity.setExternalOrderNo(request.getExternalOrderNo());
        orderEntity.setSourceType(request.getSourceType());
        orderEntity.setBizType(request.getBizType());
        orderEntity.setParkId(request.getParkId());
        orderEntity.setRouteId(request.getRouteId());
        orderEntity.setPickupPointId(request.getPickupPointId());
        orderEntity.setDropoffPointId(request.getDropoffPointId());
        orderEntity.setPickupLng(request.getPickupLng());
        orderEntity.setPickupLat(request.getPickupLat());
        orderEntity.setDropoffLng(request.getDropoffLng());
        orderEntity.setDropoffLat(request.getDropoffLat());
        if (resolution != null) {
            orderEntity.setPickupNodeCode(resolution.pickupNodeCode());
            orderEntity.setDropoffNodeCode(resolution.dropoffNodeCode());
            orderEntity.setPickupSnapMeters(resolution.pickupSnapMeters());
            orderEntity.setDropoffSnapMeters(resolution.dropoffSnapMeters());
        }
        orderEntity.setPriority(request.getPriority());
        orderEntity.setStatus(OrderStatus.WAITING_DISPATCH.name());
        orderEntity.setRemark(request.getRemark());
        orderEntity.setCreatedBy("system");
        orderEntity.setDeleted(0);

        orderMapper.insert(orderEntity);

        return OrderCreateResponse.builder()
                .orderId(orderEntity.getId())
                .orderNo(orderEntity.getOrderNo())
                .status(OrderStatus.WAITING_DISPATCH.name())
                .build();
    }

    private String generateOrderNo() {
        return "ORD" + LocalDateTime.now().format(ORDER_NO_TIME_FORMATTER)
                + ThreadLocalRandom.current().nextInt(1000, 9999);
    }
}

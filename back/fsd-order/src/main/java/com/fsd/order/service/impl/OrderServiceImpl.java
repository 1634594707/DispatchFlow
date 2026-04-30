package com.fsd.order.service.impl;
import com.fsd.common.enums.OrderStatus;
import com.fsd.order.dto.OrderCreateRequest;
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
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderNo(generateOrderNo());
        orderEntity.setExternalOrderNo(request.getExternalOrderNo());
        orderEntity.setSourceType(request.getSourceType());
        orderEntity.setBizType(request.getBizType());
        orderEntity.setPickupPointId(request.getPickupPointId());
        orderEntity.setDropoffPointId(request.getDropoffPointId());
        orderEntity.setPriority(request.getPriority());
        orderEntity.setStatus(OrderStatus.WAITING_DISPATCH.name());
        orderEntity.setRemark(request.getRemark());
        orderEntity.setCreatedBy("system");
        orderEntity.setVersion(0);
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

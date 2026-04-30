package com.fsd.order.service.impl;

import com.fsd.common.exception.BusinessException;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.order.service.OrderQueryService;
import com.fsd.order.vo.OrderDetailResponse;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryServiceImpl implements OrderQueryService {

    private final OrderMapper orderMapper;

    public OrderQueryServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public OrderDetailResponse getOrderDetail(Long id) {
        OrderEntity orderEntity = orderMapper.selectById(id);
        if (orderEntity == null || Integer.valueOf(1).equals(orderEntity.getDeleted())) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found");
        }

        return OrderDetailResponse.builder()
                .orderId(orderEntity.getId())
                .orderNo(orderEntity.getOrderNo())
                .externalOrderNo(orderEntity.getExternalOrderNo())
                .sourceType(orderEntity.getSourceType())
                .bizType(orderEntity.getBizType())
                .pickupPointId(orderEntity.getPickupPointId())
                .dropoffPointId(orderEntity.getDropoffPointId())
                .priority(orderEntity.getPriority())
                .status(orderEntity.getStatus())
                .dispatchTaskId(orderEntity.getDispatchTaskId())
                .remark(orderEntity.getRemark())
                .createdAt(orderEntity.getCreatedAt())
                .updatedAt(orderEntity.getUpdatedAt())
                .build();
    }
}

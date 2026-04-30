package com.fsd.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.order.service.OrderAdminQueryService;
import com.fsd.order.vo.OrderAdminListItemResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderAdminQueryServiceImpl implements OrderAdminQueryService {

    private final OrderMapper orderMapper;

    public OrderAdminQueryServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public List<OrderAdminListItemResponse> listOrders() {
        return orderMapper.selectList(new LambdaQueryWrapper<OrderEntity>()
                        .eq(OrderEntity::getDeleted, 0)
                        .orderByDesc(OrderEntity::getCreatedAt))
                .stream()
                .map(order -> OrderAdminListItemResponse.builder()
                        .orderId(order.getId())
                        .orderNo(order.getOrderNo())
                        .externalOrderNo(order.getExternalOrderNo())
                        .status(order.getStatus())
                        .priority(order.getPriority())
                        .dispatchTaskId(order.getDispatchTaskId())
                        .createdAt(order.getCreatedAt())
                        .updatedAt(order.getUpdatedAt())
                        .build())
                .toList();
    }
}

package com.fsd.order.service.impl;

import com.fsd.common.enums.OrderStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.order.service.OrderStateService;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderStateServiceImpl implements OrderStateService {

    private final OrderMapper orderMapper;

    public OrderStateServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public OrderEntity getOrder(Long orderId) {
        OrderEntity orderEntity = orderMapper.selectById(orderId);
        if (orderEntity == null || Integer.valueOf(1).equals(orderEntity.getDeleted())) {
            throw new BusinessException("ORDER_NOT_FOUND", "Order not found");
        }
        return orderEntity;
    }

    @Override
    @Transactional
    public void markDispatched(Long orderId, Long dispatchTaskId) {
        OrderEntity orderEntity = getOrder(orderId);
        assertStatus(orderEntity, Set.of(OrderStatus.WAITING_DISPATCH), "ORDER_STATUS_INVALID");
        orderEntity.setStatus(OrderStatus.DISPATCHED.name());
        orderEntity.setDispatchTaskId(dispatchTaskId);
        orderMapper.updateById(orderEntity);
    }

    @Override
    @Transactional
    public void markInProgress(Long orderId) {
        OrderEntity orderEntity = getOrder(orderId);
        assertStatus(orderEntity, Set.of(OrderStatus.DISPATCHED), "ORDER_STATUS_INVALID");
        orderEntity.setStatus(OrderStatus.IN_PROGRESS.name());
        orderMapper.updateById(orderEntity);
    }

    @Override
    @Transactional
    public void markCompleted(Long orderId) {
        OrderEntity orderEntity = getOrder(orderId);
        assertStatus(orderEntity, Set.of(OrderStatus.IN_PROGRESS), "ORDER_STATUS_INVALID");
        orderEntity.setStatus(OrderStatus.COMPLETED.name());
        orderMapper.updateById(orderEntity);
    }

    @Override
    @Transactional
    public void markFailed(Long orderId, String failReason) {
        OrderEntity orderEntity = getOrder(orderId);
        assertStatus(orderEntity, Set.of(OrderStatus.WAITING_DISPATCH, OrderStatus.DISPATCHED, OrderStatus.IN_PROGRESS),
                "ORDER_STATUS_INVALID");
        orderEntity.setStatus(OrderStatus.FAILED.name());
        String currentRemark = orderEntity.getRemark();
        if (failReason != null && !failReason.isBlank()) {
            orderEntity.setRemark(currentRemark == null || currentRemark.isBlank()
                    ? failReason
                    : currentRemark + "; " + failReason);
        }
        orderMapper.updateById(orderEntity);
    }

    private void assertStatus(OrderEntity orderEntity, Set<OrderStatus> allowed, String errorCode) {
        OrderStatus current = OrderStatus.valueOf(orderEntity.getStatus());
        if (!allowed.contains(current)) {
            throw new BusinessException(errorCode, "Order status transition is not allowed");
        }
    }
}

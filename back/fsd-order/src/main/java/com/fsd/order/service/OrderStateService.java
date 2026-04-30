package com.fsd.order.service;

import com.fsd.order.entity.OrderEntity;

public interface OrderStateService {

    OrderEntity getOrder(Long orderId);

    void markDispatched(Long orderId, Long dispatchTaskId);

    void markInProgress(Long orderId);

    void markCompleted(Long orderId);

    void markFailed(Long orderId, String failReason);
}

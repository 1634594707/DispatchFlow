package com.fsd.order.service;

import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.vo.OrderCreateResponse;

public interface OrderService {

    OrderCreateResponse createOrder(OrderCreateRequest request);
}

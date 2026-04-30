package com.fsd.order.service;

import com.fsd.order.vo.OrderDetailResponse;

public interface OrderQueryService {

    OrderDetailResponse getOrderDetail(Long id);
}

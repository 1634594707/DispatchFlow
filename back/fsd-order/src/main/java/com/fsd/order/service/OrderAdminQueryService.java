package com.fsd.order.service;

import com.fsd.order.vo.OrderAdminListItemResponse;
import java.util.List;

public interface OrderAdminQueryService {

    List<OrderAdminListItemResponse> listOrders();
}

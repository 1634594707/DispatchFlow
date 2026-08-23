package com.fsd.order.service;

import com.fsd.order.vo.OrderAdminListItemResponse;
import java.util.List;

public interface OrderAdminQueryService {

    List<OrderAdminListItemResponse> listOrders();

    default List<OrderAdminListItemResponse> listOrders(Long parkId) {
        return listOrders().stream()
                .filter(order -> parkId == null || parkId.equals(order.getParkId()))
                .toList();
    }
}

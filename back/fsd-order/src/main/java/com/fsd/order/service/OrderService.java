package com.fsd.order.service;

import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.dto.OrderEndpointResolution;
import com.fsd.order.vo.OrderCreateResponse;

public interface OrderService {

    /** 外部下单入口：只认预先登记的站点，坐标入口在这里会被拒（见 {@link #createOrder(OrderCreateRequest, OrderEndpointResolution)}）。 */
    default OrderCreateResponse createOrder(OrderCreateRequest request) {
        return createOrder(request, null);
    }

    /**
     * 受理入口：坐标已经由派单侧吸附成可派单路网节点后落库。
     *
     * @param resolution 服务端量出的吸附结果；纯站点单传 null
     */
    OrderCreateResponse createOrder(OrderCreateRequest request, OrderEndpointResolution resolution);
}

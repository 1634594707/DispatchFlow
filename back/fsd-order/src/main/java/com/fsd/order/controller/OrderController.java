package com.fsd.order.controller;

import com.fsd.common.model.ApiResponse;
import com.fsd.order.dto.OrderCreateRequest;
import com.fsd.order.service.OrderQueryService;
import com.fsd.order.service.OrderService;
import com.fsd.order.vo.OrderCreateResponse;
import com.fsd.order.vo.OrderDetailResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;

    public OrderController(OrderService orderService, OrderQueryService orderQueryService) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
    }

    @PostMapping
    public ApiResponse<OrderCreateResponse> createOrder(@Valid @RequestBody OrderCreateRequest request) {
        return ApiResponse.success(orderService.createOrder(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDetailResponse> getOrderDetail(@PathVariable Long id) {
        return ApiResponse.success(orderQueryService.getOrderDetail(id));
    }
}

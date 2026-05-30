package com.fsd.admin.controller;

import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.order.service.OrderQueryService;
import com.fsd.order.vo.OrderDetailResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/open/v1")
public class OpenIntegrationController {

    private final OrderQueryService orderQueryService;
    private final DispatchAdminQueryService dispatchAdminQueryService;

    public OpenIntegrationController(OrderQueryService orderQueryService,
                                     DispatchAdminQueryService dispatchAdminQueryService) {
        this.orderQueryService = orderQueryService;
        this.dispatchAdminQueryService = dispatchAdminQueryService;
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderDetailResponse> getOrder(@PathVariable Long orderId) {
        return ApiResponse.success(orderQueryService.getOrderDetail(orderId));
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<DispatchTaskDetailResponse> getTask(@PathVariable Long taskId) {
        return ApiResponse.success(dispatchAdminQueryService.getTaskDetail(taskId));
    }
}

package com.fsd.admin.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fsd.admin.dto.AdminDispatchExceptionResolveRequest;
import com.fsd.admin.dto.AdminExceptionQueryRequest;
import com.fsd.admin.dto.AdminOrderQueryRequest;
import com.fsd.admin.dto.AdminTaskQueryRequest;
import com.fsd.admin.dto.AdminVehicleQueryRequest;
import com.fsd.admin.service.AdminDashboardService;
import com.fsd.admin.service.AdminQueryFacadeService;
import com.fsd.admin.vo.AdminDashboardSummaryResponse;
import com.fsd.common.model.PageResponse;
import com.fsd.common.model.ApiResponse;
import com.fsd.dispatch.vo.DispatchExceptionListItemResponse;
import com.fsd.dispatch.vo.DispatchInterventionQueueResponse;
import com.fsd.dispatch.dto.ParkOrderCreateRequest;
import com.fsd.dispatch.service.DispatchExceptionService;
import com.fsd.dispatch.service.DispatchTaskService;
import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.MobileOrderAuthService;
import com.fsd.dispatch.service.ParkPilotCommandService;
import com.fsd.dispatch.service.ParkPilotService;
import jakarta.servlet.http.HttpServletRequest;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.DispatchTaskListItemResponse;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkOrderCreateResponse;
import com.fsd.dispatch.vo.ParkOrderSnapshotResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.order.service.OrderAdminQueryService;
import com.fsd.order.service.OrderQueryService;
import com.fsd.order.vo.OrderAdminListItemResponse;
import com.fsd.order.vo.OrderDetailResponse;
import com.fsd.vehicle.service.VehicleAdminQueryService;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDispatchControllerTest {

    @Mock
    private OrderAdminQueryService orderAdminQueryService;
    @Mock
    private OrderQueryService orderQueryService;
    @Mock
    private DispatchAdminQueryService dispatchAdminQueryService;
    @Mock
    private DispatchTaskService dispatchTaskService;
    @Mock
    private DispatchExceptionService dispatchExceptionService;
    @Mock
    private AdminDashboardService adminDashboardService;
    @Mock
    private AdminQueryFacadeService adminQueryFacadeService;
    @Mock
    private VehicleAdminQueryService vehicleAdminQueryService;
    @Mock
    private ParkPilotService parkPilotService;
    @Mock
    private ParkPilotCommandService parkPilotCommandService;
    @Mock
    private MobileOrderAuthService mobileOrderAuthService;
    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private AdminDispatchController adminDispatchController;

    @BeforeEach
    void setUpAuth() {
        lenient().when(httpServletRequest.getAttribute("fsd.admin.role")).thenReturn("ADMIN");
        lenient().when(httpServletRequest.getAttribute("fsd.admin.userId")).thenReturn(1L);
        lenient().when(httpServletRequest.getAttribute("fsd.admin.username")).thenReturn("testadmin");
        lenient().when(httpServletRequest.getAttribute("fsd.admin.displayName")).thenReturn("Test Admin");
    }

    @Test
    void shouldReturnOrders() {
        when(orderAdminQueryService.listOrders()).thenReturn(List.of(
                OrderAdminListItemResponse.builder().orderId(1L).orderNo("ORD-1").status("WAITING_DISPATCH").build()
        ));

        ApiResponse<List<OrderAdminListItemResponse>> response = adminDispatchController.listOrders(httpServletRequest);

        assertEquals(1, response.getData().size());
        assertEquals("ORD-1", response.getData().getFirst().getOrderNo());
    }

    @Test
    void shouldReturnTaskDetail() {
        when(dispatchAdminQueryService.getTaskDetail(10L)).thenReturn(
                DispatchTaskDetailResponse.builder().taskId(10L).status("ASSIGNED").build()
        );

        ApiResponse<DispatchTaskDetailResponse> response = adminDispatchController.getTaskDetail(10L, httpServletRequest);

        assertEquals(10L, response.getData().getTaskId());
        assertEquals("ASSIGNED", response.getData().getStatus());
    }

    @Test
    void shouldReturnDashboardSummary() {
        when(adminDashboardService.getSummary(null)).thenReturn(AdminDashboardSummaryResponse.builder()
                .pendingCount(1)
                .manualPendingCount(2)
                .onlineVehicleCount(3)
                .build());

        ApiResponse<AdminDashboardSummaryResponse> response = adminDispatchController.getDashboardSummary(null, httpServletRequest);

        assertEquals(2, response.getData().getManualPendingCount());
        assertEquals(3, response.getData().getOnlineVehicleCount());
    }

    @Test
    void shouldReturnOrderDetail() {
        when(orderQueryService.getOrderDetail(1L)).thenReturn(
                OrderDetailResponse.builder().orderId(1L).orderNo("ORD-1").status("DISPATCHED").build()
        );

        ApiResponse<OrderDetailResponse> response = adminDispatchController.getOrderDetail(1L, httpServletRequest);

        assertEquals("ORD-1", response.getData().getOrderNo());
    }

    @Test
    void shouldReturnExceptions() {
        when(dispatchAdminQueryService.listExceptions()).thenReturn(List.of(
                DispatchExceptionListItemResponse.builder().id(7L).taskId(3L).taskNo("TSK-3").build()
        ));

        ApiResponse<List<DispatchExceptionListItemResponse>> response = adminDispatchController.listExceptions(httpServletRequest);

        assertEquals(1, response.getData().size());
        assertEquals(7L, response.getData().getFirst().getId());
        assertEquals("TSK-3", response.getData().getFirst().getTaskNo());
    }

    @Test
    void shouldReturnInterventionQueue() {
        when(dispatchAdminQueryService.getInterventionQueue(null)).thenReturn(
                DispatchInterventionQueueResponse.builder()
                        .manualPendingCount(1)
                        .openExceptionCount(1)
                        .build()
        );

        ApiResponse<DispatchInterventionQueueResponse> response = adminDispatchController.getInterventionQueue(null, httpServletRequest);

        assertEquals(1, response.getData().getManualPendingCount());
        assertEquals(1, response.getData().getOpenExceptionCount());
    }

    @Test
    void shouldReturnTasks() {
        when(dispatchAdminQueryService.listTasks()).thenReturn(List.of(
                DispatchTaskListItemResponse.builder().taskId(3L).status("MANUAL_PENDING").build()
        ));

        ApiResponse<List<DispatchTaskListItemResponse>> response = adminDispatchController.listTasks(httpServletRequest);

        assertEquals(1, response.getData().size());
        assertEquals("MANUAL_PENDING", response.getData().getFirst().getStatus());
    }

    @Test
    void shouldReturnPagedOrders() {
        when(adminQueryFacadeService.queryOrders(any(AdminOrderQueryRequest.class))).thenReturn(
                PageResponse.<OrderAdminListItemResponse>builder().total(1).pageNo(1).pageSize(20)
                        .records(List.of(OrderAdminListItemResponse.builder().orderId(1L).build())).build()
        );
        ApiResponse<PageResponse<OrderAdminListItemResponse>> response =
                adminDispatchController.queryOrders(new AdminOrderQueryRequest(), httpServletRequest);
        assertEquals(1, response.getData().getTotal());
    }

    @Test
    void shouldReturnPagedTasks() {
        when(adminQueryFacadeService.queryTasks(any(AdminTaskQueryRequest.class))).thenReturn(
                PageResponse.<DispatchTaskListItemResponse>builder().total(1).pageNo(1).pageSize(20)
                        .records(List.of(DispatchTaskListItemResponse.builder().taskId(1L).build())).build()
        );
        ApiResponse<PageResponse<DispatchTaskListItemResponse>> response =
                adminDispatchController.queryTasks(new AdminTaskQueryRequest(), httpServletRequest);
        assertEquals(1, response.getData().getTotal());
    }

    @Test
    void shouldReturnPagedExceptions() {
        when(adminQueryFacadeService.queryExceptions(any(AdminExceptionQueryRequest.class))).thenReturn(
                PageResponse.<DispatchExceptionListItemResponse>builder().total(1).pageNo(1).pageSize(20)
                        .records(List.of(DispatchExceptionListItemResponse.builder().id(1L).build())).build()
        );
        ApiResponse<PageResponse<DispatchExceptionListItemResponse>> response =
                adminDispatchController.queryExceptions(new AdminExceptionQueryRequest(), httpServletRequest);
        assertEquals(1, response.getData().getTotal());
    }

    @Test
    void shouldReturnVehiclesAndVehicleDetail() {
        when(vehicleAdminQueryService.listVehicles()).thenReturn(List.of(
                VehicleAdminListItemResponse.builder().vehicleId(1L).vehicleCode("V-1").build()
        ));
        when(vehicleAdminQueryService.getVehicleDetail(1L)).thenReturn(
                VehicleAdminDetailResponse.builder().vehicleId(1L).vehicleCode("V-1").build()
        );

        ApiResponse<List<VehicleAdminListItemResponse>> listResponse = adminDispatchController.listVehicles(httpServletRequest);
        ApiResponse<VehicleAdminDetailResponse> detailResponse = adminDispatchController.getVehicleDetail(1L, httpServletRequest);

        assertEquals("V-1", listResponse.getData().getFirst().getVehicleCode());
        assertEquals("V-1", detailResponse.getData().getVehicleCode());
    }

    @Test
    void shouldReturnPagedVehicles() {
        when(adminQueryFacadeService.queryVehicles(any(AdminVehicleQueryRequest.class))).thenReturn(
                PageResponse.<VehicleAdminListItemResponse>builder().total(1).pageNo(1).pageSize(20)
                        .records(List.of(VehicleAdminListItemResponse.builder().vehicleId(1L).build())).build()
        );
        ApiResponse<PageResponse<VehicleAdminListItemResponse>> response =
                adminDispatchController.queryVehicles(new AdminVehicleQueryRequest(), httpServletRequest);
        assertEquals(1, response.getData().getTotal());
    }

    @Test
    void shouldResolveException() {
        AdminDispatchExceptionResolveRequest request = new AdminDispatchExceptionResolveRequest();
        request.setResolverId("u1");
        request.setResolverName("dispatcher");
        request.setAction("MARK_FAILED");
        request.setRemark("done");

        ApiResponse<Void> response = adminDispatchController.resolveException(1L, request, httpServletRequest);

        assertEquals("SUCCESS", response.getCode());
    }

    @Test
    void shouldReturnParkLayout() {
        when(parkPilotService.getLayout()).thenReturn(ParkLayoutResponse.builder()
                .width(1200)
                .height(800)
                .xFieldAlias("currentLongitude")
                .yFieldAlias("currentLatitude")
                .build());

        ApiResponse<ParkLayoutResponse> response = adminDispatchController.getParkLayout(null, httpServletRequest);

        assertEquals(1200, response.getData().getWidth());
        assertEquals("currentLongitude", response.getData().getXFieldAlias());
    }

    @Test
    void shouldReturnParkVehicles() {
        when(parkPilotService.listVehicleSnapshots()).thenReturn(List.of(
                ParkVehicleSnapshotResponse.builder().vehicleId(1L).vehicleCode("PARK-01").runtimeStage("IDLE_PATROL").build()
        ));

        ApiResponse<List<ParkVehicleSnapshotResponse>> response = adminDispatchController.listParkVehicles(httpServletRequest);

        assertEquals(1, response.getData().size());
        assertEquals("PARK-01", response.getData().getFirst().getVehicleCode());
    }

    @Test
    void shouldReturnParkStations() {
        when(parkPilotService.listStations()).thenReturn(List.of(
                ParkStationResponse.builder().stationId(101L).stationCode("A1").build()
        ));

        ApiResponse<List<ParkStationResponse>> response = adminDispatchController.listParkStations(null, httpServletRequest);

        assertEquals(1, response.getData().size());
        assertEquals("A1", response.getData().getFirst().getStationCode());
    }

    @Test
    void shouldReturnParkOrders() {
        when(parkPilotService.listOrderSnapshots()).thenReturn(List.of(
                ParkOrderSnapshotResponse.builder().orderId(1L).orderNo("ORD-1").runtimeStage("HEADING_TO_PICKUP").build()
        ));

        ApiResponse<List<ParkOrderSnapshotResponse>> response = adminDispatchController.listParkOrders(httpServletRequest);

        assertEquals(1, response.getData().size());
        assertEquals("ORD-1", response.getData().getFirst().getOrderNo());
    }

    @Test
    void shouldCreateParkOrder() {
        ParkOrderCreateRequest request = new ParkOrderCreateRequest();
        request.setPickupStationId(101L);
        request.setDropoffStationId(201L);
        request.setPriority("P1");

        when(parkPilotCommandService.createParkOrder(any(ParkOrderCreateRequest.class))).thenReturn(
                ParkOrderCreateResponse.builder()
                        .orderId(1L)
                        .orderNo("ORD-1")
                        .taskId(2L)
                        .taskNo("TASK-1")
                        .taskStatus("ASSIGNED")
                        .vehicleId(3L)
                        .message("Vehicle assigned")
                        .build()
        );

        ApiResponse<ParkOrderCreateResponse> response = adminDispatchController.createParkOrder(request, httpServletRequest);

        assertEquals(1L, response.getData().getOrderId());
        assertEquals("ASSIGNED", response.getData().getTaskStatus());
    }
}

package com.fsd.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.service.DispatchAdminQueryService;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.dispatch.vo.DispatchTaskDetailResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.order.entity.OrderEntity;
import com.fsd.order.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskAdminDetailServiceTest {

    @Mock
    private DispatchAdminQueryService dispatchAdminQueryService;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private ParkStationService parkStationService;

    @InjectMocks
    private TaskAdminDetailService taskAdminDetailService;

    @Test
    void shouldEnrichPickupAndDropoffStations() {
        when(dispatchAdminQueryService.getTaskDetail(10L)).thenReturn(
                DispatchTaskDetailResponse.builder().taskId(10L).orderId(100L).build()
        );
        OrderEntity order = new OrderEntity();
        order.setId(100L);
        order.setPickupPointId(1L);
        order.setDropoffPointId(2L);
        when(orderMapper.selectById(100L)).thenReturn(order);
        when(parkStationService.requireStation(1L)).thenReturn(
                ParkStationResponse.builder().stationCode("ZJF-PICK-01").stationName("门市 A").build()
        );
        when(parkStationService.requireStation(2L)).thenReturn(
                ParkStationResponse.builder().stationCode("ZJF-DROP-01").stationName("代发仓").build()
        );

        DispatchTaskDetailResponse detail = taskAdminDetailService.getEnrichedDetail(10L);

        assertEquals("ZJF-PICK-01", detail.getPickupStationCode());
        assertEquals("门市 A", detail.getPickupPointName());
        assertEquals("ZJF-DROP-01", detail.getDropoffStationCode());
        assertEquals("代发仓", detail.getDropoffPointName());
    }
}

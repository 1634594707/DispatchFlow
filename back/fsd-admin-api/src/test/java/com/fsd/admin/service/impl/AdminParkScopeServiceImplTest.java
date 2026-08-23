package com.fsd.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.fsd.dispatch.mapper.DispatchTaskMapper;
import com.fsd.dispatch.service.ParkStationService;
import com.fsd.order.mapper.OrderMapper;
import com.fsd.vehicle.vo.VehicleAdminListItemResponse;
import org.junit.jupiter.api.Test;

class AdminParkScopeServiceImplTest {

    @Test
    void explicitVehicleParkShouldTakePrecedenceOverCurrentOrderInference() {
        AdminParkScopeServiceImpl service = new AdminParkScopeServiceImpl(
                mock(OrderMapper.class),
                mock(DispatchTaskMapper.class),
                mock(ParkStationService.class)
        );
        VehicleAdminListItemResponse vehicle = VehicleAdminListItemResponse.builder()
                .parkId(2L)
                .currentOrderId(100L)
                .build();

        assertTrue(service.matchesVehicle(vehicle, 2L));
        assertFalse(service.matchesVehicle(vehicle, 1L));
    }
}

package com.fsd.dispatch.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fsd.common.enums.ParkingSlotStatus;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.ChargingPileEntity;
import com.fsd.dispatch.entity.ParkingSlotEntity;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ParkingSlotMapper;
import com.fsd.dispatch.service.ChargingSessionService;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.service.VehicleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParkingFacilityServiceImplTest {

    @Mock
    private ParkingSlotMapper parkingSlotMapper;
    @Mock
    private ChargingPileMapper chargingPileMapper;
    @Mock
    private ChargingSessionService chargingSessionService;
    @Mock
    private VehicleService vehicleService;

    @InjectMocks
    private ParkingFacilityServiceImpl parkingFacilityService;

    @Test
    void releaseByVehicleShouldClearSlotAndPile() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(10L);
        vehicle.setBatteryLevel(90);
        when(vehicleService.getById(10L)).thenReturn(vehicle);

        parkingFacilityService.releaseByVehicle(10L);

        verify(chargingSessionService).completeActiveSession(10L, 90);
        verify(parkingSlotMapper, atLeastOnce()).update(any(), any());
    }

    @Test
    void occupyPluggedStandbyShouldBindVehicleToSlot() {
        ParkingSlotEntity slot = new ParkingSlotEntity();
        slot.setId(1001L);
        slot.setParkId(1L);
        slot.setSlotCode("P1");
        slot.setStatus(ParkingSlotStatus.FREE.name());

        ChargingPileEntity pile = new ChargingPileEntity();
        pile.setId(2001L);
        pile.setParkingSlotId(1001L);

        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setId(42L);
        vehicle.setBatteryLevel(100);

        Page<ParkingSlotEntity> slotPage = new Page<>();
        slotPage.setRecords(java.util.List.of(slot));
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(slotPage);
        when(parkingSlotMapper.update(any(), any())).thenReturn(1);
        when(chargingPileMapper.selectList(any())).thenReturn(java.util.List.of(pile));
        when(vehicleService.getById(42L)).thenReturn(vehicle);

        parkingFacilityService.occupyPluggedStandby(1L, 42L, "P1");

        verify(chargingSessionService, atLeastOnce()).completeActiveSession(42L, 100);
        verify(parkingSlotMapper, atLeastOnce()).update(any(), any());
    }

    @Test
    void occupyShouldRejectWhenAnotherVehicleHoldsSlot() {
        ParkingSlotEntity slot = new ParkingSlotEntity();
        slot.setId(1001L);
        slot.setParkId(1L);
        slot.setSlotCode("P1");
        slot.setStatus(ParkingSlotStatus.OCCUPIED.name());
        slot.setOccupiedVehicleId(99L);

        Page<ParkingSlotEntity> slotPage = new Page<>();
        slotPage.setRecords(java.util.List.of(slot));
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(slotPage);

        assertThrows(BusinessException.class,
                () -> parkingFacilityService.occupyPluggedStandby(1L, 42L, "P1"));
    }

    @Test
    void findSlotByVehicleShouldReturnBinding() {
        ParkingSlotEntity slot = new ParkingSlotEntity();
        slot.setOccupiedVehicleId(42L);
        slot.setSlotCode("P2");
        Page<ParkingSlotEntity> slotPage = new Page<>();
        slotPage.setRecords(java.util.List.of(slot));
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(slotPage);

        var found = parkingFacilityService.findSlotByVehicle(42L);

        assertEquals("P2", found.orElseThrow().getSlotCode());
        assertEquals(42L, found.get().getOccupiedVehicleId());
    }

    @Test
    void findSlotByVehicleShouldReturnEmptyWhenUnbound() {
        Page<ParkingSlotEntity> emptyPage = new Page<>();
        emptyPage.setRecords(java.util.Collections.emptyList());
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);
        assertNull(parkingFacilityService.findSlotByVehicle(42L).orElse(null));
    }

    @Test
    void reserveSlotShouldAllowOnlyOneVehicleOnSameBay() {
        ParkingSlotEntity slot = new ParkingSlotEntity();
        slot.setId(1001L);
        slot.setParkId(1L);
        slot.setSlotCode("P1");
        slot.setStatus(ParkingSlotStatus.FREE.name());
        slot.setCoordX(java.math.BigDecimal.valueOf(80));
        slot.setCoordY(java.math.BigDecimal.valueOf(700));

        Page<ParkingSlotEntity> slotPage = new Page<>();
        slotPage.setRecords(java.util.List.of(slot));
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(slotPage);
        when(parkingSlotMapper.update(any(), any())).thenReturn(1, 1, 0);
        when(chargingPileMapper.update(any(), any())).thenReturn(1);

        assertTrue(parkingFacilityService.reserveSlot(1L, 1L, "P1"));
        assertFalse(parkingFacilityService.reserveSlot(1L, 2L, "P1"));
    }

    @Test
    void reserveChargingSlotShouldKeepPhysicalGeoCoordinates() {
        ParkingSlotEntity slot = new ParkingSlotEntity();
        slot.setId(1001L);
        slot.setParkId(1L);
        slot.setSlotCode("P1");
        slot.setStatus(ParkingSlotStatus.FREE.name());
        slot.setCoordX(java.math.BigDecimal.valueOf(668.4370));
        slot.setCoordY(java.math.BigDecimal.valueOf(624.4500));
        slot.setCoordLng(java.math.BigDecimal.valueOf(121.080681));
        slot.setCoordLat(java.math.BigDecimal.valueOf(31.960337));

        Page<ParkingSlotEntity> slotPage = new Page<>();
        slotPage.setRecords(java.util.List.of(slot));
        when(chargingPileMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(slotPage);
        when(parkingSlotMapper.update(any(), any())).thenReturn(1);

        var point = parkingFacilityService.reserveChargingSlot(1L, 42L, "P1").orElseThrow();

        assertEquals(0, java.math.BigDecimal.valueOf(121.080681).compareTo(point.getLongitude()));
        assertEquals(0, java.math.BigDecimal.valueOf(31.960337).compareTo(point.getLatitude()));
    }
}

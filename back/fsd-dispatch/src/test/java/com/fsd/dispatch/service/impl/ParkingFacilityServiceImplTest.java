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

    private ParkingSlotEntity standbySlot(String code, double lng, double lat) {
        ParkingSlotEntity slot = new ParkingSlotEntity();
        slot.setId(2001L);
        slot.setParkId(1L);
        slot.setSlotCode(code);
        slot.setSlotType(com.fsd.common.enums.ParkingSlotType.STANDBY.name());
        slot.setStatus(ParkingSlotStatus.FREE.name());
        slot.setCoordX(java.math.BigDecimal.valueOf(550.1236));
        slot.setCoordY(java.math.BigDecimal.valueOf(406.6709));
        slot.setCoordLng(java.math.BigDecimal.valueOf(lng));
        slot.setCoordLat(java.math.BigDecimal.valueOf(lat));
        return slot;
    }

    private Page<ParkingSlotEntity> pageOf(java.util.List<ParkingSlotEntity> records) {
        Page<ParkingSlotEntity> page = new Page<>();
        page.setRecords(records);
        return page;
    }

    @Test
    void reserveStandbySlotReusesTheSlotAlreadyHeldInsteadOfChurning() {
        // 空闲态每个 tick 都会问一次待命点：不续用同一个位，车就会在车位之间来回跳。
        ParkingSlotEntity held = standbySlot("P3", 121.078390, 31.961928);
        held.setStatus(ParkingSlotStatus.RESERVED.name());
        held.setOccupiedVehicleId(44L);
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(pageOf(java.util.List.of(held)));

        var point = parkingFacilityService.reserveStandbySlot(1L, 44L).orElseThrow();

        assertEquals("P3", point.getCode());
        verify(parkingSlotMapper, org.mockito.Mockito.never()).update(any(), any());
        verify(parkingSlotMapper, org.mockito.Mockito.never()).selectList(any());
    }

    @Test
    void reserveStandbySlotReservesTheFirstFreeStandbySlotInSortOrder() {
        ParkingSlotEntity slot = standbySlot("P7", 121.083128, 31.958820);
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(pageOf(java.util.Collections.emptyList()),
                        pageOf(java.util.List.of(slot)),
                        pageOf(java.util.List.of(slot)));
        when(parkingSlotMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(slot));
        when(parkingSlotMapper.update(any(), any())).thenReturn(1);

        var point = parkingFacilityService.reserveStandbySlot(1L, 45L).orElseThrow();

        assertEquals("P7", point.getCode());
        assertEquals(0, java.math.BigDecimal.valueOf(121.083128).compareTo(point.getLongitude()));
        assertEquals(0, java.math.BigDecimal.valueOf(31.958820).compareTo(point.getLatitude()));
    }

    @Test
    void reserveStandbySlotIsEmptyWhenTheParkHasNoStandbySlot() {
        // 没有车位时必须返回空，让调用方走下一级兜底 —— 不能凭空造一个坐标（那正是本次修掉的病）。
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(pageOf(java.util.Collections.emptyList()));
        when(parkingSlotMapper.selectList(any(Wrapper.class))).thenReturn(java.util.Collections.emptyList());

        assertTrue(parkingFacilityService.reserveStandbySlot(1L, 46L).isEmpty());
    }

    @Test
    void failedReserveMustNotReleaseTheSlotsTheVehicleAlreadyHolds() {
        // 回归钉：`reserveSlot` 原来在方法开头无条件 releaseReservation(vehicleId)，
        // 于是"试着去抢一个桩位"这个动作本身就会把该车已经占着的待命位释放掉
        // （实测生产 20 台里只有 6 台占得到位）。抢位失败必须一行都不动。
        ParkingSlotEntity taken = standbySlot("P9", 121.081, 31.961);
        taken.setStatus(ParkingSlotStatus.OCCUPIED.name());
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(pageOf(java.util.List.of(taken)));
        when(parkingSlotMapper.update(any(), any())).thenReturn(0);

        assertFalse(parkingFacilityService.reserveSlot(1L, 47L, "P9"));

        verify(parkingSlotMapper, org.mockito.Mockito.times(1)).update(any(), any());
        verify(chargingPileMapper, org.mockito.Mockito.never()).update(any(), any());
    }

    @Test
    void successfulReserveReleasesOnlyTheOtherReservedSlots() {
        ParkingSlotEntity free = standbySlot("P10", 121.082, 31.962);
        when(parkingSlotMapper.selectPage(any(Page.class), any(Wrapper.class)))
                .thenReturn(pageOf(java.util.List.of(free)));
        when(parkingSlotMapper.update(any(), any())).thenReturn(1);

        assertTrue(parkingFacilityService.reserveSlot(1L, 48L, "P10"));

        // 一次绑定自身 + 一次"释放该车的其它 RESERVED 位"（不是开头那次无条件释放）
        verify(parkingSlotMapper, org.mockito.Mockito.times(2)).update(any(), any());
    }
}

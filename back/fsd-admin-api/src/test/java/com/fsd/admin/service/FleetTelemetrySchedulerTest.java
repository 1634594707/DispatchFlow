package com.fsd.admin.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fsd.dispatch.service.ParkPilotService;
import com.fsd.dispatch.vo.ParkResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 性能优化方案 P0-1：每秒广播调度器空转。
 *
 * <p>{@code listVehicleSnapshots} 是 t_vehicle 全表读 + 每台车一次 Redis 运行态读 + 轨迹组装，
 * 而 {@code broadcast} 的空订阅短路发生在这之后。这里守的是"没人看大屏就不许付组装钱"。
 */
class FleetTelemetrySchedulerTest {

    private FleetTelemetryStreamService streamService;
    private ParkPilotService parkPilotService;
    private FleetTelemetryScheduler scheduler;

    @BeforeEach
    void setUp() {
        streamService = mock(FleetTelemetryStreamService.class);
        parkPilotService = mock(ParkPilotService.class);
        scheduler = new FleetTelemetryScheduler(streamService, parkPilotService);
    }

    private static ParkResponse park(long parkId) {
        return ParkResponse.builder().parkId(parkId).build();
    }

    @Test
    @DisplayName("无订阅园区：一次快照组装都不发生")
    void shouldSkipAssemblyWhenNoSubscriber() {
        when(parkPilotService.listParks()).thenReturn(List.of(park(1L), park(2L)));
        when(streamService.hasClients(anyLong())).thenReturn(false);

        scheduler.broadcastTelemetry();

        verify(parkPilotService, never()).listVehicleSnapshots(anyLong());
        verify(streamService, never()).broadcast(anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("混合园区：只组装有订阅者的那个园区")
    void shouldAssembleOnlySubscribedPark() {
        when(parkPilotService.listParks()).thenReturn(List.of(park(1L), park(2L)));
        when(streamService.hasClients(2L)).thenReturn(true);
        List<ParkVehicleSnapshotResponse> snapshots = List.of();
        when(parkPilotService.listVehicleSnapshots(2L)).thenReturn(snapshots);

        scheduler.broadcastTelemetry();

        verify(parkPilotService, never()).listVehicleSnapshots(1L);
        verify(parkPilotService, times(1)).listVehicleSnapshots(2L);
        verify(streamService, times(1)).broadcast(2L, snapshots);
    }
}

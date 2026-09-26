package com.fsd.dispatch.service;

import com.fsd.dispatch.vo.ParkGeofenceResponse;
import com.fsd.dispatch.vo.ParkLayoutResponse;
import com.fsd.dispatch.vo.ParkOrderSnapshotResponse;
import com.fsd.dispatch.vo.ParkOverviewResponse;
import com.fsd.dispatch.vo.ParkResponse;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.dispatch.vo.ParkTrackResponse;
import com.fsd.dispatch.vo.ParkVehicleSnapshotResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import java.util.List;

public interface ParkPilotService {

    List<ParkResponse> listParks();

    ParkLayoutResponse getLayout();

    ParkLayoutResponse getLayout(Long parkId);

    List<ParkStationResponse> listStations();

    List<ParkStationResponse> listStations(Long parkId);

    ParkStationResponse getStation(Long stationId);

    VehicleEntity selectNearestVehicle(List<VehicleEntity> candidates, Long stationId);

    List<ParkVehicleSnapshotResponse> listVehicleSnapshots();

    List<ParkVehicleSnapshotResponse> listVehicleSnapshots(Long parkId);

    List<ParkOrderSnapshotResponse> listOrderSnapshots();

    default List<ParkOrderSnapshotResponse> listOrderSnapshots(Long parkId) {
        return listOrderSnapshots();
    }

    List<ParkGeofenceResponse> listGeofences(Long parkId);

    /**
     * 一次追踪聚合读（§16.3）：{@code orderId} 给了就必须命中且属于该园区，不给就替调用者挑
     * 最近一条还在进行的单；车辆只回派给这一单的那台。
     *
     * <p>与整园的 {@code listOrderSnapshots}/{@code listVehicleSnapshots} 的区别不是"少几个字段"，
     * 而是**读的规模**：那两个全表读 + 35 台车各带三条折线，移动页每 1.5 s 打一次，
     * 实测一次轮询 209 KB、积压一上来 p95 就从 155 ms 走到 1.07 s。
     * 整园那两个原样留给大屏/工作台，本方法不改动它们。
     *
     * @param recentLimit 最近列表条数，服务端裁到 1–20
     */
    ParkTrackResponse buildTrackSnapshot(Long parkId, Long orderId, int recentLimit);

    List<ParkOverviewResponse> listParkOverview();
}

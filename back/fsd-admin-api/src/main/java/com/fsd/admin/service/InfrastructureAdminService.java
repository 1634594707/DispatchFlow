package com.fsd.admin.service;

import com.fsd.admin.dto.AdminBatterySwapCabinetUpsertRequest;
import com.fsd.admin.dto.AdminChargingPileUpsertRequest;
import com.fsd.admin.dto.AdminParkUpsertRequest;
import com.fsd.admin.dto.AdminParkingSlotUpsertRequest;
import com.fsd.admin.dto.AdminRoadNodeUpsertRequest;
import com.fsd.admin.dto.AdminRoadSegmentUpsertRequest;
import com.fsd.admin.dto.AdminStationUpsertRequest;
import com.fsd.admin.vo.AdminBatterySwapCabinetResponse;
import com.fsd.admin.vo.AdminChargingPileResponse;
import com.fsd.admin.vo.AdminGeofenceResponse;
import com.fsd.admin.vo.AdminParkResponse;
import com.fsd.admin.vo.AdminParkingSlotResponse;
import com.fsd.admin.vo.AdminRoadNodeResponse;
import com.fsd.admin.vo.AdminRoadSegmentResponse;
import com.fsd.admin.vo.AdminStationResponse;
import java.util.List;

public interface InfrastructureAdminService {

    List<AdminParkResponse> listParks();

    AdminParkResponse createPark(AdminParkUpsertRequest request);

    AdminParkResponse updatePark(Long parkId, AdminParkUpsertRequest request);

    AdminParkResponse toggleParkStatus(Long parkId);

    List<AdminStationResponse> listStations(Long parkId);

    AdminStationResponse createStation(AdminStationUpsertRequest request);

    AdminStationResponse updateStation(Long stationId, AdminStationUpsertRequest request);

    List<AdminParkingSlotResponse> listParkingSlots(Long parkId);

    AdminParkingSlotResponse createParkingSlot(AdminParkingSlotUpsertRequest request);

    AdminParkingSlotResponse updateParkingSlot(Long slotId, AdminParkingSlotUpsertRequest request);

    List<AdminChargingPileResponse> listChargingPiles(Long parkId);

    AdminChargingPileResponse createChargingPile(AdminChargingPileUpsertRequest request);

    AdminChargingPileResponse updateChargingPile(Long pileId, AdminChargingPileUpsertRequest request);

    List<AdminBatterySwapCabinetResponse> listBatterySwapCabinets(Long parkId);

    AdminBatterySwapCabinetResponse createBatterySwapCabinet(AdminBatterySwapCabinetUpsertRequest request);

    AdminBatterySwapCabinetResponse updateBatterySwapCabinet(Long cabinetId, AdminBatterySwapCabinetUpsertRequest request);

    void deleteBatterySwapCabinet(Long cabinetId);

    List<AdminRoadNodeResponse> listRoadNodes(Long parkId);

    AdminRoadNodeResponse createRoadNode(AdminRoadNodeUpsertRequest request);

    AdminRoadNodeResponse updateRoadNode(Long nodeId, AdminRoadNodeUpsertRequest request);

    List<AdminRoadSegmentResponse> listRoadSegments(Long parkId);

    AdminRoadSegmentResponse createRoadSegment(AdminRoadSegmentUpsertRequest request);

    AdminRoadSegmentResponse updateRoadSegment(Long segmentId, AdminRoadSegmentUpsertRequest request);

    AdminRoadSegmentResponse toggleRoadSegmentStatus(Long segmentId);

    List<AdminGeofenceResponse> listGeofences(Long parkId);

    AdminGeofenceResponse createGeofence(com.fsd.admin.dto.AdminGeofenceUpsertRequest request);

    AdminGeofenceResponse updateGeofence(Long geofenceId, com.fsd.admin.dto.AdminGeofenceUpdateRequest request);

    void deleteGeofence(Long geofenceId);
}

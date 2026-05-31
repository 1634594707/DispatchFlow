package com.fsd.admin.controller;

import com.fsd.admin.auth.AdminAuthSupport;
import com.fsd.admin.dto.AdminBatterySwapCabinetUpsertRequest;
import com.fsd.admin.dto.AdminChargingPileUpsertRequest;
import com.fsd.admin.dto.AdminParkUpsertRequest;
import com.fsd.admin.dto.AdminParkingSlotUpsertRequest;
import com.fsd.admin.dto.AdminRoadNodeUpsertRequest;
import com.fsd.admin.dto.AdminRoadSegmentUpsertRequest;
import com.fsd.admin.dto.AdminStationUpsertRequest;
import com.fsd.admin.service.InfrastructureAdminService;
import com.fsd.admin.vo.AdminBatterySwapCabinetResponse;
import com.fsd.admin.vo.AdminChargingPileResponse;
import com.fsd.admin.vo.AdminParkResponse;
import com.fsd.admin.vo.AdminParkingSlotResponse;
import com.fsd.admin.vo.AdminRoadNodeResponse;
import com.fsd.admin.vo.AdminRoadSegmentResponse;
import com.fsd.admin.vo.AdminStationResponse;
import com.fsd.common.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/infrastructure")
public class AdminInfrastructureController {

    private final InfrastructureAdminService infrastructureAdminService;

    public AdminInfrastructureController(InfrastructureAdminService infrastructureAdminService) {
        this.infrastructureAdminService = infrastructureAdminService;
    }

    @GetMapping("/parks")
    public ApiResponse<List<AdminParkResponse>> listParks(HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.listParks());
    }

    @PostMapping("/parks")
    public ApiResponse<AdminParkResponse> createPark(@Valid @RequestBody AdminParkUpsertRequest body,
                                                     HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.createPark(body));
    }

    @PutMapping("/parks/{parkId}")
    public ApiResponse<AdminParkResponse> updatePark(@PathVariable Long parkId,
                                                     @Valid @RequestBody AdminParkUpsertRequest body,
                                                     HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.updatePark(parkId, body));
    }

    @PostMapping("/parks/{parkId}/toggle-status")
    public ApiResponse<AdminParkResponse> toggleParkStatus(@PathVariable Long parkId,
                                                           HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.toggleParkStatus(parkId));
    }

    @GetMapping("/stations")
    public ApiResponse<List<AdminStationResponse>> listStations(@RequestParam(required = false) Long parkId,
                                                                HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.listStations(parkId));
    }

    @PostMapping("/stations")
    public ApiResponse<AdminStationResponse> createStation(@Valid @RequestBody AdminStationUpsertRequest body,
                                                           HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.createStation(body));
    }

    @PutMapping("/stations/{stationId}")
    public ApiResponse<AdminStationResponse> updateStation(@PathVariable Long stationId,
                                                           @Valid @RequestBody AdminStationUpsertRequest body,
                                                           HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.updateStation(stationId, body));
    }

    @GetMapping("/parking-slots")
    public ApiResponse<List<AdminParkingSlotResponse>> listParkingSlots(@RequestParam(required = false) Long parkId,
                                                                        HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.listParkingSlots(parkId));
    }

    @PostMapping("/parking-slots")
    public ApiResponse<AdminParkingSlotResponse> createParkingSlot(@Valid @RequestBody AdminParkingSlotUpsertRequest body,
                                                                   HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.createParkingSlot(body));
    }

    @PutMapping("/parking-slots/{slotId}")
    public ApiResponse<AdminParkingSlotResponse> updateParkingSlot(@PathVariable Long slotId,
                                                                   @Valid @RequestBody AdminParkingSlotUpsertRequest body,
                                                                   HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.updateParkingSlot(slotId, body));
    }

    @GetMapping("/charging-piles")
    public ApiResponse<List<AdminChargingPileResponse>> listChargingPiles(@RequestParam(required = false) Long parkId,
                                                                          HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.listChargingPiles(parkId));
    }

    @PostMapping("/charging-piles")
    public ApiResponse<AdminChargingPileResponse> createChargingPile(@Valid @RequestBody AdminChargingPileUpsertRequest body,
                                                                       HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.createChargingPile(body));
    }

    @PutMapping("/charging-piles/{pileId}")
    public ApiResponse<AdminChargingPileResponse> updateChargingPile(@PathVariable Long pileId,
                                                                     @Valid @RequestBody AdminChargingPileUpsertRequest body,
                                                                     HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.updateChargingPile(pileId, body));
    }

    @GetMapping("/swap-cabinets")
    public ApiResponse<List<AdminBatterySwapCabinetResponse>> listSwapCabinets(
            @RequestParam(required = false) Long parkId,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.listBatterySwapCabinets(parkId));
    }

    @PostMapping("/swap-cabinets")
    public ApiResponse<AdminBatterySwapCabinetResponse> createSwapCabinet(
            @Valid @RequestBody AdminBatterySwapCabinetUpsertRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.createBatterySwapCabinet(body));
    }

    @PutMapping("/swap-cabinets/{cabinetId}")
    public ApiResponse<AdminBatterySwapCabinetResponse> updateSwapCabinet(
            @PathVariable Long cabinetId,
            @Valid @RequestBody AdminBatterySwapCabinetUpsertRequest body,
            HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.updateBatterySwapCabinet(cabinetId, body));
    }

    @PostMapping("/swap-cabinets/{cabinetId}/delete")
    public ApiResponse<Void> deleteSwapCabinet(@PathVariable Long cabinetId, HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        infrastructureAdminService.deleteBatterySwapCabinet(cabinetId);
        return ApiResponse.success(null);
    }

    @GetMapping("/road-nodes")
    public ApiResponse<List<AdminRoadNodeResponse>> listRoadNodes(@RequestParam(required = false) Long parkId,
                                                                  HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.listRoadNodes(parkId));
    }

    @PostMapping("/road-nodes")
    public ApiResponse<AdminRoadNodeResponse> createRoadNode(@Valid @RequestBody AdminRoadNodeUpsertRequest body,
                                                             HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.createRoadNode(body));
    }

    @PutMapping("/road-nodes/{nodeId}")
    public ApiResponse<AdminRoadNodeResponse> updateRoadNode(@PathVariable Long nodeId,
                                                             @Valid @RequestBody AdminRoadNodeUpsertRequest body,
                                                             HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.updateRoadNode(nodeId, body));
    }

    @GetMapping("/road-segments")
    public ApiResponse<List<AdminRoadSegmentResponse>> listRoadSegments(@RequestParam(required = false) Long parkId,
                                                                        HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.listRoadSegments(parkId));
    }

    @PostMapping("/road-segments")
    public ApiResponse<AdminRoadSegmentResponse> createRoadSegment(@Valid @RequestBody AdminRoadSegmentUpsertRequest body,
                                                                   HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.createRoadSegment(body));
    }

    @PutMapping("/road-segments/{segmentId}")
    public ApiResponse<AdminRoadSegmentResponse> updateRoadSegment(@PathVariable Long segmentId,
                                                                   @Valid @RequestBody AdminRoadSegmentUpsertRequest body,
                                                                   HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.updateRoadSegment(segmentId, body));
    }

    @PostMapping("/road-segments/{segmentId}/toggle-status")
    public ApiResponse<AdminRoadSegmentResponse> toggleRoadSegmentStatus(@PathVariable Long segmentId,
                                                                         HttpServletRequest request) {
        AdminAuthSupport.requireAdmin(request);
        return ApiResponse.success(infrastructureAdminService.toggleRoadSegmentStatus(segmentId));
    }
}

package com.fsd.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.fsd.common.enums.ParkStatus;
import com.fsd.common.enums.ParkingSlotStatus;
import com.fsd.common.enums.ParkingSlotType;
import com.fsd.common.enums.StationType;
import com.fsd.common.exception.BusinessException;
import com.fsd.dispatch.entity.BatterySwapCabinetEntity;
import com.fsd.dispatch.entity.ChargingPileEntity;
import com.fsd.dispatch.entity.ParkEntity;
import com.fsd.dispatch.entity.ParkingSlotEntity;
import com.fsd.dispatch.entity.RoadNodeEntity;
import com.fsd.dispatch.entity.RoadSegmentEntity;
import com.fsd.dispatch.entity.StationEntity;
import com.fsd.dispatch.mapper.BatterySwapCabinetMapper;
import com.fsd.dispatch.mapper.ChargingPileMapper;
import com.fsd.dispatch.mapper.ParkMapper;
import com.fsd.dispatch.mapper.ParkingSlotMapper;
import com.fsd.dispatch.mapper.RoadNodeMapper;
import com.fsd.dispatch.mapper.RoadSegmentMapper;
import com.fsd.dispatch.geo.local.ZjfStationGeoAdminService;
import com.fsd.dispatch.mapper.StationMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InfrastructureAdminServiceImpl implements InfrastructureAdminService {

    private static final String ROAD_ACTIVE = "ACTIVE";
    private static final String ROAD_DISABLED = "DISABLED";

    private final ParkMapper parkMapper;
    private final StationMapper stationMapper;
    private final ParkingSlotMapper parkingSlotMapper;
    private final ChargingPileMapper chargingPileMapper;
    private final BatterySwapCabinetMapper batterySwapCabinetMapper;
    private final RoadNodeMapper roadNodeMapper;
    private final RoadSegmentMapper roadSegmentMapper;
    private final com.fsd.dispatch.service.ParkGeofenceService parkGeofenceService;
    private final ZjfStationGeoAdminService zjfStationGeoAdminService;

    public InfrastructureAdminServiceImpl(ParkMapper parkMapper,
                                          StationMapper stationMapper,
                                          ParkingSlotMapper parkingSlotMapper,
                                          ChargingPileMapper chargingPileMapper,
                                          BatterySwapCabinetMapper batterySwapCabinetMapper,
                                          RoadNodeMapper roadNodeMapper,
                                          RoadSegmentMapper roadSegmentMapper,
                                          com.fsd.dispatch.service.ParkGeofenceService parkGeofenceService,
                                          ZjfStationGeoAdminService zjfStationGeoAdminService) {
        this.parkMapper = parkMapper;
        this.stationMapper = stationMapper;
        this.parkingSlotMapper = parkingSlotMapper;
        this.chargingPileMapper = chargingPileMapper;
        this.batterySwapCabinetMapper = batterySwapCabinetMapper;
        this.roadNodeMapper = roadNodeMapper;
        this.roadSegmentMapper = roadSegmentMapper;
        this.parkGeofenceService = parkGeofenceService;
        this.zjfStationGeoAdminService = zjfStationGeoAdminService;
    }

    @Override
    public List<AdminParkResponse> listParks() {
        return parkMapper.selectList(new LambdaQueryWrapper<ParkEntity>()
                        .eq(ParkEntity::getDeleted, 0)
                        .orderByDesc(ParkEntity::getDefaultFlag)
                        .orderByAsc(ParkEntity::getId))
                .stream()
                .map(this::toParkResponse)
                .toList();
    }

    @Override
    @Transactional
    public AdminParkResponse createPark(AdminParkUpsertRequest request) {
        ensureUniqueParkCode(request.getParkCode(), null);
        ParkEntity park = new ParkEntity();
        applyParkFields(park, request);
        park.setStatus(resolveParkStatus(request.getStatus(), ParkStatus.ACTIVE.name()));
        park.setDefaultFlag(Boolean.TRUE.equals(request.getDefaultPark()) ? 1 : 0);
        park.setDeleted(0);
        park.setVersion(0);
        if (park.getDefaultFlag() != null && park.getDefaultFlag() == 1) {
            clearDefaultParkFlag(null);
        }
        parkMapper.insert(park);
        return toParkResponse(park);
    }

    @Override
    @Transactional
    public AdminParkResponse updatePark(Long parkId, AdminParkUpsertRequest request) {
        ParkEntity park = requirePark(parkId);
        ensureUniqueParkCode(request.getParkCode(), parkId);
        applyParkFields(park, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            park.setStatus(resolveParkStatus(request.getStatus(), park.getStatus()));
        }
        if (request.getDefaultPark() != null) {
            if (request.getDefaultPark()) {
                clearDefaultParkFlag(parkId);
                park.setDefaultFlag(1);
            } else if (park.getDefaultFlag() != null && park.getDefaultFlag() == 1) {
                throw new BusinessException("PARK_DEFAULT_REQUIRED", "至少保留一个默认园区");
            }
        }
        parkMapper.updateById(park);
        return toParkResponse(park);
    }

    @Override
    @Transactional
    public AdminParkResponse toggleParkStatus(Long parkId) {
        ParkEntity park = requirePark(parkId);
        if (park.getDefaultFlag() != null && park.getDefaultFlag() == 1
                && ParkStatus.ACTIVE.name().equals(park.getStatus())) {
            throw new BusinessException("PARK_DEFAULT_DISABLE", "默认园区不能停用");
        }
        park.setStatus(ParkStatus.ACTIVE.name().equals(park.getStatus())
                ? ParkStatus.INACTIVE.name()
                : ParkStatus.ACTIVE.name());
        parkMapper.updateById(park);
        return toParkResponse(park);
    }

    @Override
    public List<AdminStationResponse> listStations(Long parkId) {
        Map<Long, ParkEntity> parks = parkNameMap();
        LambdaQueryWrapper<StationEntity> wrapper = new LambdaQueryWrapper<StationEntity>()
                .eq(StationEntity::getDeleted, 0)
                .orderByAsc(StationEntity::getSortOrder)
                .orderByAsc(StationEntity::getId);
        if (parkId != null) {
            wrapper.eq(StationEntity::getParkId, parkId);
        }
        return stationMapper.selectList(wrapper).stream()
                .map(station -> toStationResponse(station, parks.get(station.getParkId())))
                .toList();
    }

    @Override
    @Transactional
    public AdminStationResponse createStation(AdminStationUpsertRequest request) {
        ParkEntity park = requirePark(request.getParkId());
        ensureUniqueStationCode(request.getParkId(), request.getStationCode(), null);
        StationEntity station = new StationEntity();
        applyStationFields(station, request);
        zjfStationGeoAdminService.snapAndValidate(station);
        station.setStatus(resolveStationStatus(request.getStatus(), "ACTIVE"));
        station.setDeleted(0);
        station.setVersion(0);
        stationMapper.insert(station);
        return toStationResponse(station, park);
    }

    @Override
    @Transactional
    public AdminStationResponse updateStation(Long stationId, AdminStationUpsertRequest request) {
        StationEntity station = requireStation(stationId);
        requirePark(request.getParkId());
        ensureUniqueStationCode(request.getParkId(), request.getStationCode(), stationId);
        applyStationFields(station, request);
        zjfStationGeoAdminService.snapAndValidate(station);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            station.setStatus(resolveStationStatus(request.getStatus(), station.getStatus()));
        }
        stationMapper.updateById(station);
        return toStationResponse(station, requirePark(station.getParkId()));
    }

    @Override
    public List<AdminParkingSlotResponse> listParkingSlots(Long parkId) {
        Map<Long, ParkEntity> parks = parkNameMap();
        LambdaQueryWrapper<ParkingSlotEntity> wrapper = new LambdaQueryWrapper<ParkingSlotEntity>()
                .eq(ParkingSlotEntity::getDeleted, 0)
                .orderByAsc(ParkingSlotEntity::getSortOrder)
                .orderByAsc(ParkingSlotEntity::getId);
        if (parkId != null) {
            wrapper.eq(ParkingSlotEntity::getParkId, parkId);
        }
        return parkingSlotMapper.selectList(wrapper).stream()
                .map(slot -> toParkingSlotResponse(slot, parks.get(slot.getParkId())))
                .toList();
    }

    @Override
    @Transactional
    public AdminParkingSlotResponse createParkingSlot(AdminParkingSlotUpsertRequest request) {
        ParkEntity park = requirePark(request.getParkId());
        ensureUniqueSlotCode(request.getParkId(), request.getSlotCode(), null);
        ParkingSlotEntity slot = new ParkingSlotEntity();
        applyParkingSlotFields(slot, request);
        slot.setStatus(resolveSlotStatus(request.getStatus(), ParkingSlotStatus.FREE.name()));
        slot.setDeleted(0);
        slot.setVersion(0);
        parkingSlotMapper.insert(slot);
        return toParkingSlotResponse(slot, park);
    }

    @Override
    @Transactional
    public AdminParkingSlotResponse updateParkingSlot(Long slotId, AdminParkingSlotUpsertRequest request) {
        ParkingSlotEntity slot = requireParkingSlot(slotId);
        requirePark(request.getParkId());
        ensureUniqueSlotCode(request.getParkId(), request.getSlotCode(), slotId);
        applyParkingSlotFields(slot, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            slot.setStatus(resolveSlotStatus(request.getStatus(), slot.getStatus()));
        }
        parkingSlotMapper.updateById(slot);
        return toParkingSlotResponse(slot, requirePark(slot.getParkId()));
    }

    @Override
    public List<AdminChargingPileResponse> listChargingPiles(Long parkId) {
        Map<Long, ParkEntity> parks = parkNameMap();
        Map<Long, ParkingSlotEntity> slots = parkingSlotMapper.selectList(new LambdaQueryWrapper<ParkingSlotEntity>()
                        .eq(ParkingSlotEntity::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(ParkingSlotEntity::getId, Function.identity()));
        LambdaQueryWrapper<ChargingPileEntity> wrapper = new LambdaQueryWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getDeleted, 0)
                .orderByAsc(ChargingPileEntity::getSortOrder)
                .orderByAsc(ChargingPileEntity::getId);
        if (parkId != null) {
            wrapper.eq(ChargingPileEntity::getParkId, parkId);
        }
        return chargingPileMapper.selectList(wrapper).stream()
                .map(pile -> toChargingPileResponse(pile, parks.get(pile.getParkId()), slots.get(pile.getParkingSlotId())))
                .toList();
    }

    @Override
    @Transactional
    public AdminChargingPileResponse createChargingPile(AdminChargingPileUpsertRequest request) {
        ParkEntity park = requirePark(request.getParkId());
        ParkingSlotEntity slot = requireParkingSlot(request.getParkingSlotId());
        if (!Objects.equals(slot.getParkId(), request.getParkId())) {
            throw new BusinessException("PILE_SLOT_PARK_MISMATCH", "绑定车位不属于该园区");
        }
        ensureUniquePileCode(request.getParkId(), request.getPileCode(), null);
        ensureSlotNotBound(request.getParkingSlotId(), null);
        ChargingPileEntity pile = new ChargingPileEntity();
        applyChargingPileFields(pile, request);
        pile.setStatus(resolveSlotStatus(request.getStatus(), ParkingSlotStatus.FREE.name()));
        pile.setDeleted(0);
        pile.setVersion(0);
        chargingPileMapper.insert(pile);
        return toChargingPileResponse(pile, park, slot);
    }

    @Override
    @Transactional
    public AdminChargingPileResponse updateChargingPile(Long pileId, AdminChargingPileUpsertRequest request) {
        ChargingPileEntity pile = requireChargingPile(pileId);
        ParkEntity park = requirePark(request.getParkId());
        ParkingSlotEntity slot = requireParkingSlot(request.getParkingSlotId());
        if (!Objects.equals(slot.getParkId(), request.getParkId())) {
            throw new BusinessException("PILE_SLOT_PARK_MISMATCH", "绑定车位不属于该园区");
        }
        ensureUniquePileCode(request.getParkId(), request.getPileCode(), pileId);
        ensureSlotNotBound(request.getParkingSlotId(), pileId);
        applyChargingPileFields(pile, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            pile.setStatus(resolveSlotStatus(request.getStatus(), pile.getStatus()));
        }
        chargingPileMapper.updateById(pile);
        return toChargingPileResponse(pile, park, slot);
    }

    @Override
    public List<AdminBatterySwapCabinetResponse> listBatterySwapCabinets(Long parkId) {
        Map<Long, ParkEntity> parks = parkNameMap();
        LambdaQueryWrapper<BatterySwapCabinetEntity> wrapper = new LambdaQueryWrapper<BatterySwapCabinetEntity>()
                .eq(BatterySwapCabinetEntity::getDeleted, 0)
                .orderByAsc(BatterySwapCabinetEntity::getCabinetCode);
        if (parkId != null) {
            wrapper.eq(BatterySwapCabinetEntity::getParkId, parkId);
        }
        return batterySwapCabinetMapper.selectList(wrapper).stream()
                .map(cabinet -> toBatterySwapCabinetResponse(cabinet, parks.get(cabinet.getParkId())))
                .toList();
    }

    @Override
    @Transactional
    public AdminBatterySwapCabinetResponse createBatterySwapCabinet(AdminBatterySwapCabinetUpsertRequest request) {
        ParkEntity park = requirePark(request.getParkId());
        ensureUniqueCabinetCode(request.getParkId(), request.getCabinetCode(), null);
        BatterySwapCabinetEntity cabinet = new BatterySwapCabinetEntity();
        applyBatterySwapCabinetFields(cabinet, request);
        cabinet.setStatus(resolveCabinetStatus(request.getStatus(), "ACTIVE"));
        cabinet.setDeleted(0);
        batterySwapCabinetMapper.insert(cabinet);
        return toBatterySwapCabinetResponse(cabinet, park);
    }

    @Override
    @Transactional
    public AdminBatterySwapCabinetResponse updateBatterySwapCabinet(Long cabinetId,
                                                                    AdminBatterySwapCabinetUpsertRequest request) {
        BatterySwapCabinetEntity cabinet = requireBatterySwapCabinet(cabinetId);
        ParkEntity park = requirePark(request.getParkId());
        ensureUniqueCabinetCode(request.getParkId(), request.getCabinetCode(), cabinetId);
        applyBatterySwapCabinetFields(cabinet, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            cabinet.setStatus(resolveCabinetStatus(request.getStatus(), cabinet.getStatus()));
        }
        batterySwapCabinetMapper.updateById(cabinet);
        return toBatterySwapCabinetResponse(cabinet, park);
    }

    @Override
    @Transactional
    public void deleteBatterySwapCabinet(Long cabinetId) {
        BatterySwapCabinetEntity cabinet = requireBatterySwapCabinet(cabinetId);
        cabinet.setDeleted(1);
        batterySwapCabinetMapper.updateById(cabinet);
    }

    @Override
    public List<AdminRoadNodeResponse> listRoadNodes(Long parkId) {
        Map<Long, ParkEntity> parks = parkNameMap();
        LambdaQueryWrapper<RoadNodeEntity> wrapper = new LambdaQueryWrapper<RoadNodeEntity>()
                .eq(RoadNodeEntity::getDeleted, 0)
                .orderByAsc(RoadNodeEntity::getNodeCode);
        if (parkId != null) {
            wrapper.eq(RoadNodeEntity::getParkId, parkId);
        }
        return roadNodeMapper.selectList(wrapper).stream()
                .map(node -> toRoadNodeResponse(node, parks.get(node.getParkId())))
                .toList();
    }

    @Override
    @Transactional
    public AdminRoadNodeResponse createRoadNode(AdminRoadNodeUpsertRequest request) {
        ParkEntity park = requirePark(request.getParkId());
        ensureUniqueNodeCode(request.getParkId(), request.getNodeCode(), null);
        RoadNodeEntity node = new RoadNodeEntity();
        applyRoadNodeFields(node, request);
        node.setStatus(resolveRoadStatus(request.getStatus(), ROAD_ACTIVE));
        node.setDeleted(0);
        node.setVersion(0);
        roadNodeMapper.insert(node);
        return toRoadNodeResponse(node, park);
    }

    @Override
    @Transactional
    public AdminRoadNodeResponse updateRoadNode(Long nodeId, AdminRoadNodeUpsertRequest request) {
        RoadNodeEntity node = requireRoadNode(nodeId);
        requirePark(request.getParkId());
        ensureUniqueNodeCode(request.getParkId(), request.getNodeCode(), nodeId);
        applyRoadNodeFields(node, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            node.setStatus(resolveRoadStatus(request.getStatus(), node.getStatus()));
        }
        roadNodeMapper.updateById(node);
        return toRoadNodeResponse(node, requirePark(node.getParkId()));
    }

    @Override
    public List<AdminRoadSegmentResponse> listRoadSegments(Long parkId) {
        Map<Long, ParkEntity> parks = parkNameMap();
        LambdaQueryWrapper<RoadSegmentEntity> wrapper = new LambdaQueryWrapper<RoadSegmentEntity>()
                .eq(RoadSegmentEntity::getDeleted, 0)
                .orderByAsc(RoadSegmentEntity::getFromNodeCode)
                .orderByAsc(RoadSegmentEntity::getToNodeCode);
        if (parkId != null) {
            wrapper.eq(RoadSegmentEntity::getParkId, parkId);
        }
        return roadSegmentMapper.selectList(wrapper).stream()
                .map(segment -> toRoadSegmentResponse(segment, parks.get(segment.getParkId())))
                .toList();
    }

    @Override
    @Transactional
    public AdminRoadSegmentResponse createRoadSegment(AdminRoadSegmentUpsertRequest request) {
        ParkEntity park = requirePark(request.getParkId());
        ensureRoadNodesExist(request.getParkId(), request.getFromNodeCode(), request.getToNodeCode());
        ensureUniqueSegment(request.getParkId(), request.getFromNodeCode(), request.getToNodeCode(), null);
        RoadSegmentEntity segment = new RoadSegmentEntity();
        applyRoadSegmentFields(segment, request);
        segment.setStatus(resolveRoadStatus(request.getStatus(), ROAD_ACTIVE));
        segment.setDeleted(0);
        segment.setVersion(0);
        roadSegmentMapper.insert(segment);
        return toRoadSegmentResponse(segment, park);
    }

    @Override
    @Transactional
    public AdminRoadSegmentResponse updateRoadSegment(Long segmentId, AdminRoadSegmentUpsertRequest request) {
        RoadSegmentEntity segment = requireRoadSegment(segmentId);
        requirePark(request.getParkId());
        ensureRoadNodesExist(request.getParkId(), request.getFromNodeCode(), request.getToNodeCode());
        ensureUniqueSegment(request.getParkId(), request.getFromNodeCode(), request.getToNodeCode(), segmentId);
        applyRoadSegmentFields(segment, request);
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            segment.setStatus(resolveRoadStatus(request.getStatus(), segment.getStatus()));
        }
        roadSegmentMapper.updateById(segment);
        return toRoadSegmentResponse(segment, requirePark(segment.getParkId()));
    }

    @Override
    @Transactional
    public AdminRoadSegmentResponse toggleRoadSegmentStatus(Long segmentId) {
        RoadSegmentEntity segment = requireRoadSegment(segmentId);
        segment.setStatus(ROAD_ACTIVE.equals(segment.getStatus()) ? ROAD_DISABLED : ROAD_ACTIVE);
        roadSegmentMapper.updateById(segment);
        return toRoadSegmentResponse(segment, requirePark(segment.getParkId()));
    }

    @Override
    public List<com.fsd.admin.vo.AdminGeofenceResponse> listGeofences(Long parkId) {
        Map<Long, ParkEntity> parks = parkNameMap();
        return parkGeofenceService.listByPark(parkId).stream()
                .map(item -> toGeofenceResponse(item, parks.get(item.getParkId())))
                .toList();
    }

    @Override
    @Transactional
    public com.fsd.admin.vo.AdminGeofenceResponse createGeofence(com.fsd.admin.dto.AdminGeofenceUpsertRequest request) {
        ParkEntity park = requirePark(request.getParkId());
        var created = parkGeofenceService.create(
                request.getParkId(),
                request.getFenceCode(),
                request.getFenceName(),
                request.getFenceType(),
                request.getPolygonJson(),
                request.getRemark());
        return toGeofenceResponse(created, park);
    }

    @Override
    @Transactional
    public com.fsd.admin.vo.AdminGeofenceResponse updateGeofence(Long geofenceId,
                                                                 com.fsd.admin.dto.AdminGeofenceUpdateRequest request) {
        var updated = parkGeofenceService.update(
                geofenceId,
                request.getFenceName(),
                request.getFenceType(),
                request.getPolygonJson(),
                request.getStatus(),
                request.getRemark());
        return toGeofenceResponse(updated, requirePark(updated.getParkId()));
    }

    @Override
    @Transactional
    public void deleteGeofence(Long geofenceId) {
        parkGeofenceService.delete(geofenceId);
    }

    private Map<Long, ParkEntity> parkNameMap() {
        return parkMapper.selectList(new LambdaQueryWrapper<ParkEntity>()
                        .eq(ParkEntity::getDeleted, 0))
                .stream()
                .collect(Collectors.toMap(ParkEntity::getId, Function.identity()));
    }

    private void clearDefaultParkFlag(Long exceptParkId) {
        List<ParkEntity> defaults = parkMapper.selectList(new LambdaQueryWrapper<ParkEntity>()
                .eq(ParkEntity::getDeleted, 0)
                .eq(ParkEntity::getDefaultFlag, 1));
        for (ParkEntity entity : defaults) {
            if (exceptParkId == null || !exceptParkId.equals(entity.getId())) {
                entity.setDefaultFlag(0);
                parkMapper.updateById(entity);
            }
        }
    }

    private void applyParkFields(ParkEntity park, AdminParkUpsertRequest request) {
        park.setParkCode(request.getParkCode().trim());
        park.setParkName(request.getParkName().trim());
        park.setMapWidth(request.getMapWidth());
        park.setMapHeight(request.getMapHeight());
        park.setMinZoom(request.getMinZoom());
        park.setMaxZoom(request.getMaxZoom());
        park.setVehicleSpeedPxPerSecond(request.getVehicleSpeedPxPerSecond());
        park.setRemark(request.getRemark());
    }

    private void applyStationFields(StationEntity station, AdminStationUpsertRequest request) {
        validateStationType(request.getStationType());
        station.setParkId(request.getParkId());
        station.setStationCode(request.getStationCode().trim());
        station.setStationName(request.getStationName().trim());
        station.setStationType(request.getStationType());
        station.setCoordX(request.getCoordX());
        station.setCoordY(request.getCoordY());
        station.setCoordLng(request.getCoordLng());
        station.setCoordLat(request.getCoordLat());
        station.setArea(request.getArea());
        station.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        station.setCapacityLimit(request.getCapacityLimit());
        station.setRemark(request.getRemark());
    }

    private void applyParkingSlotFields(ParkingSlotEntity slot, AdminParkingSlotUpsertRequest request) {
        validateSlotType(request.getSlotType());
        slot.setParkId(request.getParkId());
        slot.setSlotCode(request.getSlotCode().trim());
        slot.setSlotName(request.getSlotName().trim());
        slot.setSlotType(request.getSlotType() != null && !request.getSlotType().isBlank()
                ? request.getSlotType()
                : ParkingSlotType.STANDBY.name());
        slot.setCoordX(request.getCoordX());
        slot.setCoordY(request.getCoordY());
        slot.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        slot.setRemark(request.getRemark());
    }

    private void applyChargingPileFields(ChargingPileEntity pile, AdminChargingPileUpsertRequest request) {
        pile.setParkId(request.getParkId());
        pile.setPileCode(request.getPileCode().trim());
        pile.setPileName(request.getPileName().trim());
        pile.setParkingSlotId(request.getParkingSlotId());
        pile.setMaxPowerKw(request.getMaxPowerKw());
        pile.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        pile.setRemark(request.getRemark());
    }

    private void applyRoadNodeFields(RoadNodeEntity node, AdminRoadNodeUpsertRequest request) {
        node.setParkId(request.getParkId());
        node.setNodeCode(request.getNodeCode().trim());
        node.setCoordX(request.getCoordX());
        node.setCoordY(request.getCoordY());
        node.setRemark(request.getRemark());
    }

    private void applyRoadSegmentFields(RoadSegmentEntity segment, AdminRoadSegmentUpsertRequest request) {
        segment.setParkId(request.getParkId());
        segment.setFromNodeCode(request.getFromNodeCode().trim());
        segment.setToNodeCode(request.getToNodeCode().trim());
        if (request.getSpeedLimitKmh() != null) {
            segment.setSpeedLimitKmh(request.getSpeedLimitKmh());
        }
        if (request.getCongestionLevel() != null) {
            segment.setCongestionLevel(request.getCongestionLevel());
        }
        segment.setRemark(request.getRemark());
    }

    private void ensureUniqueParkCode(String parkCode, Long exceptId) {
        ParkEntity existing = parkMapper.selectOne(new LambdaQueryWrapper<ParkEntity>()
                .eq(ParkEntity::getParkCode, parkCode.trim())
                .eq(ParkEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !exceptId.equals(existing.getId()))) {
            throw new BusinessException("PARK_CODE_EXISTS", "园区编码已存在");
        }
    }

    private void ensureUniqueStationCode(Long parkId, String stationCode, Long exceptId) {
        StationEntity existing = stationMapper.selectOne(new LambdaQueryWrapper<StationEntity>()
                .eq(StationEntity::getParkId, parkId)
                .eq(StationEntity::getStationCode, stationCode.trim())
                .eq(StationEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !exceptId.equals(existing.getId()))) {
            throw new BusinessException("STATION_CODE_EXISTS", "站点编码已存在");
        }
    }

    private void ensureUniqueSlotCode(Long parkId, String slotCode, Long exceptId) {
        ParkingSlotEntity existing = parkingSlotMapper.selectOne(new LambdaQueryWrapper<ParkingSlotEntity>()
                .eq(ParkingSlotEntity::getParkId, parkId)
                .eq(ParkingSlotEntity::getSlotCode, slotCode.trim())
                .eq(ParkingSlotEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !exceptId.equals(existing.getId()))) {
            throw new BusinessException("SLOT_CODE_EXISTS", "车位编码已存在");
        }
    }

    private void ensureUniquePileCode(Long parkId, String pileCode, Long exceptId) {
        ChargingPileEntity existing = chargingPileMapper.selectOne(new LambdaQueryWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getParkId, parkId)
                .eq(ChargingPileEntity::getPileCode, pileCode.trim())
                .eq(ChargingPileEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !exceptId.equals(existing.getId()))) {
            throw new BusinessException("PILE_CODE_EXISTS", "充电桩编码已存在");
        }
    }

    private void ensureSlotNotBound(Long parkingSlotId, Long exceptPileId) {
        ChargingPileEntity existing = chargingPileMapper.selectOne(new LambdaQueryWrapper<ChargingPileEntity>()
                .eq(ChargingPileEntity::getParkingSlotId, parkingSlotId)
                .eq(ChargingPileEntity::getDeleted, 0));
        if (existing != null && (exceptPileId == null || !exceptPileId.equals(existing.getId()))) {
            throw new BusinessException("SLOT_ALREADY_BOUND", "该车位已绑定其他充电桩");
        }
    }

    private void ensureUniqueNodeCode(Long parkId, String nodeCode, Long exceptId) {
        RoadNodeEntity existing = roadNodeMapper.selectOne(new LambdaQueryWrapper<RoadNodeEntity>()
                .eq(RoadNodeEntity::getParkId, parkId)
                .eq(RoadNodeEntity::getNodeCode, nodeCode.trim())
                .eq(RoadNodeEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !exceptId.equals(existing.getId()))) {
            throw new BusinessException("ROAD_NODE_EXISTS", "路网节点编码已存在");
        }
    }

    private void ensureUniqueSegment(Long parkId, String from, String to, Long exceptId) {
        RoadSegmentEntity existing = roadSegmentMapper.selectOne(new LambdaQueryWrapper<RoadSegmentEntity>()
                .eq(RoadSegmentEntity::getParkId, parkId)
                .eq(RoadSegmentEntity::getFromNodeCode, from.trim())
                .eq(RoadSegmentEntity::getToNodeCode, to.trim())
                .eq(RoadSegmentEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !exceptId.equals(existing.getId()))) {
            throw new BusinessException("ROAD_SEGMENT_EXISTS", "路网边已存在");
        }
    }

    private void ensureRoadNodesExist(Long parkId, String from, String to) {
        if (from.trim().equals(to.trim())) {
            throw new BusinessException("ROAD_SEGMENT_SELF_LOOP", "路网边起终点不能相同");
        }
        requireNodeByCode(parkId, from);
        requireNodeByCode(parkId, to);
    }

    private RoadNodeEntity requireNodeByCode(Long parkId, String nodeCode) {
        RoadNodeEntity node = roadNodeMapper.selectOne(new LambdaQueryWrapper<RoadNodeEntity>()
                .eq(RoadNodeEntity::getParkId, parkId)
                .eq(RoadNodeEntity::getNodeCode, nodeCode.trim())
                .eq(RoadNodeEntity::getDeleted, 0));
        if (node == null) {
            throw new BusinessException("ROAD_NODE_NOT_FOUND", "路网节点不存在: " + nodeCode);
        }
        return node;
    }

    private ParkEntity requirePark(Long parkId) {
        ParkEntity park = parkMapper.selectById(parkId);
        if (park == null || park.getDeleted() != null && park.getDeleted() != 0) {
            throw new BusinessException("PARK_NOT_FOUND", "园区不存在");
        }
        return park;
    }

    private StationEntity requireStation(Long stationId) {
        StationEntity station = stationMapper.selectById(stationId);
        if (station == null || station.getDeleted() != null && station.getDeleted() != 0) {
            throw new BusinessException("STATION_NOT_FOUND", "站点不存在");
        }
        return station;
    }

    private ParkingSlotEntity requireParkingSlot(Long slotId) {
        ParkingSlotEntity slot = parkingSlotMapper.selectById(slotId);
        if (slot == null || slot.getDeleted() != null && slot.getDeleted() != 0) {
            throw new BusinessException("SLOT_NOT_FOUND", "车位不存在");
        }
        return slot;
    }

    private ChargingPileEntity requireChargingPile(Long pileId) {
        ChargingPileEntity pile = chargingPileMapper.selectById(pileId);
        if (pile == null || pile.getDeleted() != null && pile.getDeleted() != 0) {
            throw new BusinessException("PILE_NOT_FOUND", "充电桩不存在");
        }
        return pile;
    }

    private RoadNodeEntity requireRoadNode(Long nodeId) {
        RoadNodeEntity node = roadNodeMapper.selectById(nodeId);
        if (node == null || node.getDeleted() != null && node.getDeleted() != 0) {
            throw new BusinessException("ROAD_NODE_NOT_FOUND", "路网节点不存在");
        }
        return node;
    }

    private RoadSegmentEntity requireRoadSegment(Long segmentId) {
        RoadSegmentEntity segment = roadSegmentMapper.selectById(segmentId);
        if (segment == null || segment.getDeleted() != null && segment.getDeleted() != 0) {
            throw new BusinessException("ROAD_SEGMENT_NOT_FOUND", "路网边不存在");
        }
        return segment;
    }

    private String resolveParkStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        try {
            return ParkStatus.valueOf(status).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("PARK_STATUS_INVALID", "无效的园区状态");
        }
    }

    private String resolveStationStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return "INACTIVE".equals(status) ? "INACTIVE" : "ACTIVE";
    }

    private String resolveSlotStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        try {
            return ParkingSlotStatus.valueOf(status).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("SLOT_STATUS_INVALID", "无效的车位/充电桩状态");
        }
    }

    private String resolveRoadStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        if (ROAD_ACTIVE.equals(status) || ROAD_DISABLED.equals(status)) {
            return status;
        }
        throw new BusinessException("ROAD_STATUS_INVALID", "无效的路网状态");
    }

    private void validateStationType(String stationType) {
        try {
            StationType.valueOf(stationType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("STATION_TYPE_INVALID", "无效的站点类型");
        }
    }

    private void validateSlotType(String slotType) {
        if (slotType == null || slotType.isBlank()) {
            return;
        }
        try {
            ParkingSlotType.valueOf(slotType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("SLOT_TYPE_INVALID", "无效的车位类型");
        }
    }

    private AdminParkResponse toParkResponse(ParkEntity park) {
        return AdminParkResponse.builder()
                .id(park.getId())
                .parkCode(park.getParkCode())
                .parkName(park.getParkName())
                .mapWidth(park.getMapWidth())
                .mapHeight(park.getMapHeight())
                .minZoom(park.getMinZoom())
                .maxZoom(park.getMaxZoom())
                .vehicleSpeedPxPerSecond(park.getVehicleSpeedPxPerSecond())
                .status(park.getStatus())
                .defaultPark(park.getDefaultFlag() != null && park.getDefaultFlag() == 1)
                .remark(park.getRemark())
                .createdAt(park.getCreatedAt())
                .updatedAt(park.getUpdatedAt())
                .build();
    }

    private AdminStationResponse toStationResponse(StationEntity station, ParkEntity park) {
        return AdminStationResponse.builder()
                .id(station.getId())
                .parkId(station.getParkId())
                .parkName(park != null ? park.getParkName() : null)
                .stationCode(station.getStationCode())
                .stationName(station.getStationName())
                .stationType(station.getStationType())
                .coordX(station.getCoordX())
                .coordY(station.getCoordY())
                .coordLng(station.getCoordLng())
                .coordLat(station.getCoordLat())
                .area(station.getArea())
                .status(station.getStatus())
                .sortOrder(station.getSortOrder())
                .capacityLimit(station.getCapacityLimit())
                .anchorNodeCode(station.getAnchorNodeCode())
                .serviceDirection(station.getServiceDirection())
                .allowedVehicleTypes(station.getAllowedVehicleTypes())
                .unreachableReason(station.getUnreachableReason())
                .unreachableUntil(station.getUnreachableUntil())
                .remark(station.getRemark())
                .createdAt(station.getCreatedAt())
                .updatedAt(station.getUpdatedAt())
                .build();
    }

    private AdminParkingSlotResponse toParkingSlotResponse(ParkingSlotEntity slot, ParkEntity park) {
        return AdminParkingSlotResponse.builder()
                .id(slot.getId())
                .parkId(slot.getParkId())
                .parkName(park != null ? park.getParkName() : null)
                .slotCode(slot.getSlotCode())
                .slotName(slot.getSlotName())
                .slotType(slot.getSlotType())
                .coordX(slot.getCoordX())
                .coordY(slot.getCoordY())
                .status(slot.getStatus())
                .occupiedVehicleId(slot.getOccupiedVehicleId())
                .sortOrder(slot.getSortOrder())
                .facingDirection(slot.getFacingDirection())
                .entryNodeCode(slot.getEntryNodeCode())
                .exitNodeCode(slot.getExitNodeCode())
                .blockingMainRoad(slot.getBlockingMainRoad())
                .remark(slot.getRemark())
                .createdAt(slot.getCreatedAt())
                .updatedAt(slot.getUpdatedAt())
                .build();
    }

    private AdminChargingPileResponse toChargingPileResponse(ChargingPileEntity pile,
                                                               ParkEntity park,
                                                               ParkingSlotEntity slot) {
        return AdminChargingPileResponse.builder()
                .id(pile.getId())
                .parkId(pile.getParkId())
                .parkName(park != null ? park.getParkName() : null)
                .pileCode(pile.getPileCode())
                .pileName(pile.getPileName())
                .parkingSlotId(pile.getParkingSlotId())
                .parkingSlotCode(slot != null ? slot.getSlotCode() : null)
                .status(pile.getStatus())
                .occupiedVehicleId(pile.getOccupiedVehicleId())
                .maxPowerKw(pile.getMaxPowerKw())
                .sortOrder(pile.getSortOrder())
                .entryNodeCode(pile.getEntryNodeCode())
                .exitNodeCode(pile.getExitNodeCode())
                .plugType(pile.getPlugType())
                .reservationState(pile.getReservationState())
                .estimatedReleaseAt(pile.getEstimatedReleaseAt())
                .remark(pile.getRemark())
                .createdAt(pile.getCreatedAt())
                .updatedAt(pile.getUpdatedAt())
                .build();
    }

    private AdminRoadNodeResponse toRoadNodeResponse(RoadNodeEntity node, ParkEntity park) {
        return AdminRoadNodeResponse.builder()
                .id(node.getId())
                .parkId(node.getParkId())
                .parkName(park != null ? park.getParkName() : null)
                .nodeCode(node.getNodeCode())
                .coordX(node.getCoordX())
                .coordY(node.getCoordY())
                .status(node.getStatus())
                .remark(node.getRemark())
                .createdAt(node.getCreatedAt())
                .updatedAt(node.getUpdatedAt())
                .build();
    }

    private AdminRoadSegmentResponse toRoadSegmentResponse(RoadSegmentEntity segment, ParkEntity park) {
        return AdminRoadSegmentResponse.builder()
                .id(segment.getId())
                .parkId(segment.getParkId())
                .parkName(park != null ? park.getParkName() : null)
                .fromNodeCode(segment.getFromNodeCode())
                .toNodeCode(segment.getToNodeCode())
                .status(segment.getStatus())
                .speedLimitKmh(segment.getSpeedLimitKmh())
                .congestionLevel(segment.getCongestionLevel())
                .widthMeters(segment.getWidthMeters())
                .roadClass(segment.getRoadClass())
                .accessState(segment.getAccessState())
                .polylineGeojson(segment.getPolylineGeojson())
                .allowedVehicleTypes(segment.getAllowedVehicleTypes())
                .turnRestriction(segment.getTurnRestriction())
                .gateCode(segment.getGateCode())
                .blockReason(segment.getBlockReason())
                .blockedFrom(segment.getBlockedFrom())
                .blockedUntil(segment.getBlockedUntil())
                .remark(segment.getRemark())
                .createdAt(segment.getCreatedAt())
                .updatedAt(segment.getUpdatedAt())
                .build();
    }

    private void applyBatterySwapCabinetFields(BatterySwapCabinetEntity cabinet,
                                               AdminBatterySwapCabinetUpsertRequest request) {
        cabinet.setParkId(request.getParkId());
        cabinet.setCabinetCode(request.getCabinetCode().trim());
        cabinet.setCabinetName(request.getCabinetName().trim());
        cabinet.setCoordX(request.getCoordX());
        cabinet.setCoordY(request.getCoordY());
        cabinet.setSlotCount(request.getSlotCount() != null ? request.getSlotCount() : 4);
        cabinet.setRemark(request.getRemark());
    }

    private void ensureUniqueCabinetCode(Long parkId, String cabinetCode, Long exceptId) {
        BatterySwapCabinetEntity existing = batterySwapCabinetMapper.selectOne(new LambdaQueryWrapper<BatterySwapCabinetEntity>()
                .eq(BatterySwapCabinetEntity::getParkId, parkId)
                .eq(BatterySwapCabinetEntity::getCabinetCode, cabinetCode.trim())
                .eq(BatterySwapCabinetEntity::getDeleted, 0));
        if (existing != null && (exceptId == null || !exceptId.equals(existing.getId()))) {
            throw new BusinessException("SWAP_CABINET_CODE_EXISTS", "换电柜编码已存在");
        }
    }

    private BatterySwapCabinetEntity requireBatterySwapCabinet(Long cabinetId) {
        BatterySwapCabinetEntity cabinet = batterySwapCabinetMapper.selectById(cabinetId);
        if (cabinet == null || cabinet.getDeleted() != null && cabinet.getDeleted() != 0) {
            throw new BusinessException("SWAP_CABINET_NOT_FOUND", "换电柜不存在");
        }
        return cabinet;
    }

    private String resolveCabinetStatus(String status, String fallback) {
        if (status == null || status.isBlank()) {
            return fallback;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private AdminBatterySwapCabinetResponse toBatterySwapCabinetResponse(BatterySwapCabinetEntity cabinet,
                                                                         ParkEntity park) {
        return AdminBatterySwapCabinetResponse.builder()
                .id(cabinet.getId())
                .parkId(cabinet.getParkId())
                .parkName(park != null ? park.getParkName() : null)
                .cabinetCode(cabinet.getCabinetCode())
                .cabinetName(cabinet.getCabinetName())
                .coordX(cabinet.getCoordX())
                .coordY(cabinet.getCoordY())
                .slotCount(cabinet.getSlotCount())
                .status(cabinet.getStatus())
                .remark(cabinet.getRemark())
                .createdAt(cabinet.getCreatedAt())
                .updatedAt(cabinet.getUpdatedAt())
                .build();
    }

    private com.fsd.admin.vo.AdminGeofenceResponse toGeofenceResponse(com.fsd.dispatch.vo.ParkGeofenceResponse item,
                                                                      ParkEntity park) {
        return com.fsd.admin.vo.AdminGeofenceResponse.builder()
                .id(item.getId())
                .parkId(item.getParkId())
                .parkName(park != null ? park.getParkName() : null)
                .fenceCode(item.getFenceCode())
                .fenceName(item.getFenceName())
                .fenceType(item.getFenceType())
                .polygon(item.getPolygon())
                .status(item.getStatus())
                .remark(item.getRemark())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}

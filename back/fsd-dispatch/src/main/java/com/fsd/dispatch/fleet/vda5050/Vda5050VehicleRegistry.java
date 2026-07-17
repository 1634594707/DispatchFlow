package com.fsd.dispatch.fleet.vda5050;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class Vda5050VehicleRegistry {

    private final VehicleMapper vehicleMapper;

    public Vda5050VehicleRegistry(VehicleMapper vehicleMapper) {
        this.vehicleMapper = vehicleMapper;
    }

    public Optional<VehicleEntity> findByIdentity(String manufacturer, String serialNumber) {
        if (manufacturer == null || manufacturer.isBlank() || serialNumber == null || serialNumber.isBlank()) {
            return Optional.empty();
        }
        Page<VehicleEntity> vehiclePage = vehicleMapper.selectPage(new Page<>(1, 1, false), new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getLinkMode, VehicleLinkMode.VDA5050.name())
                .eq(VehicleEntity::getVdaManufacturer, manufacturer.trim())
                .eq(VehicleEntity::getVdaSerialNumber, serialNumber.trim()));
        List<VehicleEntity> vehicleRecords = vehiclePage.getRecords();
        VehicleEntity vehicle = vehicleRecords.isEmpty() ? null : vehicleRecords.get(0);
        return Optional.ofNullable(vehicle);
    }

    public String resolveInterfaceName(VehicleEntity vehicle) {
        if (vehicle.getVdaInterfaceName() != null && !vehicle.getVdaInterfaceName().isBlank()) {
            return vehicle.getVdaInterfaceName().trim();
        }
        return "uagv/v2";
    }
}

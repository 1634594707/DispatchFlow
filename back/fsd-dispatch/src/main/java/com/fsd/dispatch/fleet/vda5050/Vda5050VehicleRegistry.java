package com.fsd.dispatch.fleet.vda5050;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.vehicle.entity.VehicleEntity;
import com.fsd.vehicle.mapper.VehicleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        VehicleEntity vehicle = vehicleMapper.selectOne(new LambdaQueryWrapper<VehicleEntity>()
                .eq(VehicleEntity::getDeleted, 0)
                .eq(VehicleEntity::getLinkMode, VehicleLinkMode.VDA5050.name())
                .eq(VehicleEntity::getVdaManufacturer, manufacturer.trim())
                .eq(VehicleEntity::getVdaSerialNumber, serialNumber.trim())
                .last("limit 1"));
        return Optional.ofNullable(vehicle);
    }

    public String resolveInterfaceName(VehicleEntity vehicle) {
        if (vehicle.getVdaInterfaceName() != null && !vehicle.getVdaInterfaceName().isBlank()) {
            return vehicle.getVdaInterfaceName().trim();
        }
        return "uagv/v2";
    }
}

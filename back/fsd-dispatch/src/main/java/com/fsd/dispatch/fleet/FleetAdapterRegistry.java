package com.fsd.dispatch.fleet;

import com.fsd.common.enums.VehicleLinkMode;
import com.fsd.common.exception.BusinessException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FleetAdapterRegistry {

    private final Map<VehicleLinkMode, FleetAdapter> adapters = new EnumMap<>(VehicleLinkMode.class);

    public FleetAdapterRegistry(List<FleetAdapter> adapterList) {
        for (FleetAdapter adapter : adapterList) {
            adapters.put(adapter.supportedLinkMode(), adapter);
        }
    }

    public FleetAdapter require(VehicleLinkMode linkMode) {
        FleetAdapter adapter = adapters.get(linkMode);
        if (adapter == null) {
            throw new BusinessException("FLEET_ADAPTER_NOT_FOUND", "No fleet adapter for " + linkMode);
        }
        return adapter;
    }

    @SuppressWarnings("unchecked")
    public <T extends FleetAdapter> T require(VehicleLinkMode linkMode, Class<T> type) {
        FleetAdapter adapter = require(linkMode);
        if (!type.isInstance(adapter)) {
            throw new BusinessException("FLEET_ADAPTER_TYPE_MISMATCH", "Adapter type mismatch for " + linkMode);
        }
        return (T) adapter;
    }
}

package com.fsd.dispatch.fleet.vda5050;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fsd.vehicle.entity.VehicleEntity;
import org.junit.jupiter.api.Test;

class Vda5050TopicHelperTest {

    @Test
    void shouldBuildOrderTopicFromVehicle() {
        VehicleEntity vehicle = new VehicleEntity();
        vehicle.setVdaManufacturer("DispatchFlow");
        vehicle.setVdaSerialNumber("AGV-001");
        vehicle.setVdaInterfaceName("uagv/v2");

        Vda5050VehicleRegistry registry = new Vda5050VehicleRegistry(null);
        assertEquals("uagv/v2/DispatchFlow/AGV-001/order", Vda5050TopicHelper.orderTopic(vehicle, registry));
    }

    @Test
    void shouldParseStateTopic() {
        Vda5050TopicHelper.TopicIdentity identity = Vda5050TopicHelper.parseTopic(
                "uagv/v2/DispatchFlow/AGV-001/state");
        assertNotNull(identity);
        assertEquals("uagv/v2", identity.interfaceName());
        assertEquals("DispatchFlow", identity.manufacturer());
        assertEquals("AGV-001", identity.serialNumber());
        assertEquals("state", identity.channel());
    }
}

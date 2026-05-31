package com.fsd.dispatch.fleet.vda5050;

import com.fsd.vehicle.entity.VehicleEntity;

public final class Vda5050TopicHelper {

    private Vda5050TopicHelper() {
    }

    public static String stateSubscription(String interfaceName) {
        return interfaceName + "/+/+/state";
    }

    public static String orderTopic(VehicleEntity vehicle, Vda5050VehicleRegistry registry) {
        return channelTopic(registry.resolveInterfaceName(vehicle),
                vehicle.getVdaManufacturer(),
                vehicle.getVdaSerialNumber(),
                "order");
    }

    public static String channelTopic(String interfaceName, String manufacturer, String serialNumber, String channel) {
        return interfaceName + "/" + manufacturer + "/" + serialNumber + "/" + channel;
    }

    public static TopicIdentity parseTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return null;
        }
        String[] parts = topic.split("/");
        if (parts.length < 5) {
            return null;
        }
        String channel = parts[parts.length - 1];
        String serialNumber = parts[parts.length - 2];
        String manufacturer = parts[parts.length - 3];
        StringBuilder iface = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length - 3; i++) {
            iface.append('/').append(parts[i]);
        }
        return new TopicIdentity(iface.toString(), manufacturer, serialNumber, channel);
    }

    public record TopicIdentity(String interfaceName, String manufacturer, String serialNumber, String channel) {
    }
}

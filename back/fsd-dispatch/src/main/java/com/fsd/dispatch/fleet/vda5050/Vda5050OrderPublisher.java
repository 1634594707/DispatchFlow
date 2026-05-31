package com.fsd.dispatch.fleet.vda5050;

import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.VehicleCommandEntity;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Vda5050OrderPublisher implements Vda5050DispatchPublisher {

    private static final Logger log = LoggerFactory.getLogger(Vda5050OrderPublisher.class);

    private final Vda5050OrderBuilder orderBuilder;
    private final Vda5050VehicleRegistry vehicleRegistry;
    private final Vda5050MqttGateway mqttGateway;

    public Vda5050OrderPublisher(Vda5050OrderBuilder orderBuilder,
                                 Vda5050VehicleRegistry vehicleRegistry,
                                 Vda5050MqttGateway mqttGateway) {
        this.orderBuilder = orderBuilder;
        this.vehicleRegistry = vehicleRegistry;
        this.mqttGateway = mqttGateway;
    }

    public void publishDispatchOrder(VehicleEntity vehicle,
                                     VehicleCommandEntity command,
                                     DispatchTaskEntity task,
                                     ParkStationResponse pickup,
                                     ParkStationResponse dropoff) {
        if (!mqttGateway.isConnected()) {
            log.warn("VDA5050 MQTT not connected; order for {} kept in command queue", vehicle.getVehicleCode());
            return;
        }
        String topic = Vda5050TopicHelper.orderTopic(vehicle, vehicleRegistry);
        String payload = orderBuilder.buildDispatchOrder(vehicle, command, task, pickup, dropoff);
        mqttGateway.publish(topic, payload);
        log.info("Published VDA5050 order to {} for vehicle {}", topic, vehicle.getVehicleCode());
    }
}

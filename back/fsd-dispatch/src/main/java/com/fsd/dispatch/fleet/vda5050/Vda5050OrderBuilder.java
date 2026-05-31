package com.fsd.dispatch.fleet.vda5050;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fsd.dispatch.entity.DispatchTaskEntity;
import com.fsd.dispatch.entity.VehicleCommandEntity;
import com.fsd.dispatch.vo.ParkStationResponse;
import com.fsd.vehicle.entity.VehicleEntity;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class Vda5050OrderBuilder {

    private final ObjectMapper objectMapper;
    private final AtomicLong headerSeq = new AtomicLong(1);

    public Vda5050OrderBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String buildDispatchOrder(VehicleEntity vehicle,
                                     VehicleCommandEntity command,
                                     DispatchTaskEntity task,
                                     ParkStationResponse pickup,
                                     ParkStationResponse dropoff) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("headerId", headerSeq.getAndIncrement());
        root.put("timestamp", Instant.now().toString());
        root.put("version", "2.0.0");
        root.put("manufacturer", vehicle.getVdaManufacturer());
        root.put("serialNumber", vehicle.getVdaSerialNumber());
        root.put("orderId", "task-" + task.getId());
        root.put("orderUpdateId", command.getId());
        root.put("zoneSetId", "default");

        ArrayNode nodes = root.putArray("nodes");
        nodes.add(buildNode("pickup", 0, pickup));
        nodes.add(buildNode("dropoff", 2, dropoff));

        ArrayNode edges = root.putArray("edges");
        ObjectNode edge = edges.addObject();
        edge.put("edgeId", "pickup-dropoff");
        edge.put("sequenceId", 1);
        edge.put("startNodeId", "pickup");
        edge.put("endNodeId", "dropoff");
        edge.put("released", true);

        return root.toString();
    }

    private ObjectNode buildNode(String nodeId, int sequenceId, ParkStationResponse station) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("nodeId", nodeId);
        node.put("sequenceId", sequenceId);
        node.put("released", true);
        node.put("nodeDescription", station.getStationName());
        ObjectNode position = node.putObject("nodePosition");
        position.put("x", station.getX() != null ? station.getX().doubleValue() : 0);
        position.put("y", station.getY() != null ? station.getY().doubleValue() : 0);
        position.put("theta", 0);
        position.put("mapId", "default");
        return node;
    }
}

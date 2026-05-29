package com.fsd.dispatch.service;

import com.fsd.dispatch.dto.VehicleTelemetryRequest;
import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.vo.VehicleReportResponse;

public interface VehicleGatewayService {

    void ingestTelemetry(VehicleTelemetryRequest request);

    VehicleReportResponse handleReport(VehicleReportRequest request);
}

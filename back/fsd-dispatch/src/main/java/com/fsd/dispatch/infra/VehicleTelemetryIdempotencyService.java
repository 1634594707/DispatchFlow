package com.fsd.dispatch.infra;

import com.fsd.dispatch.dto.VehicleTelemetryRequest;

public interface VehicleTelemetryIdempotencyService {

    boolean markIfFirstTelemetry(VehicleTelemetryRequest request);
}

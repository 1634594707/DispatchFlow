package com.fsd.dispatch.infra;

import com.fsd.vehicle.dto.VehicleReportRequest;

public interface DispatchReportIdempotencyService {

    boolean markIfFirstReport(VehicleReportRequest request);
}

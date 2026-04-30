package com.fsd.vehicle.service;

import com.fsd.vehicle.dto.VehicleReportRequest;
import com.fsd.vehicle.vo.VehicleReportResponse;

public interface VehicleReportService {

    VehicleReportResponse handleReport(VehicleReportRequest request);
}

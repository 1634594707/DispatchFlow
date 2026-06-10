package com.fsd.admin.service;

import com.fsd.admin.dto.AdminDigitalTwinSimulateRequest;
import com.fsd.admin.vo.AdminDigitalTwinSimulateResponse;
import com.fsd.admin.vo.AdminDigitalTwinSnapshotResponse;

public interface DigitalTwinAdminService {

    AdminDigitalTwinSnapshotResponse getSnapshot(Long parkId);

    AdminDigitalTwinSimulateResponse simulate(AdminDigitalTwinSimulateRequest request);
}

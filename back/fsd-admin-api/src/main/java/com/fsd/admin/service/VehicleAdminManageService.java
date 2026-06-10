package com.fsd.admin.service;

import com.fsd.admin.dto.AdminVehicleMaintenanceUpsertRequest;
import com.fsd.admin.dto.AdminVehicleUpsertRequest;
import com.fsd.admin.vo.AdminVehicleCredentialResponse;
import com.fsd.admin.vo.AdminVehicleMaintenanceResponse;
import com.fsd.vehicle.vo.VehicleAdminDetailResponse;
import java.util.List;

public interface VehicleAdminManageService {

    VehicleAdminDetailResponse createVehicle(AdminVehicleUpsertRequest request);

    VehicleAdminDetailResponse updateVehicle(Long vehicleId, AdminVehicleUpsertRequest request);

    void disableVehicle(Long vehicleId);

    List<AdminVehicleCredentialResponse> listCredentials(Long vehicleId);

    AdminVehicleCredentialResponse createCredential(Long vehicleId);

    void disableCredential(Long credentialId);

    List<AdminVehicleMaintenanceResponse> listMaintenanceRecords(Long vehicleId);

    AdminVehicleMaintenanceResponse createMaintenanceRecord(AdminVehicleMaintenanceUpsertRequest request,
                                                            String operatorName);
}
